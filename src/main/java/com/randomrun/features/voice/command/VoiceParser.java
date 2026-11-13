package com.randomrun.features.voice.command;

import com.randomrun.features.voice.command.VoiceCommand.Dir;
import com.randomrun.features.voice.command.VoiceLexicon.Kind;
import com.randomrun.features.voice.command.VoiceLexicon.Tok;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns recognized speech into commands. Works on meanings, not exact phrases:
 * "иди вперёд", "вперёд", "шагай прямо" all land on the same Move. A phrase
 * can hold several commands joined by "и", "потом", "then"; a part without its
 * own verb borrows the previous one, so "скрафти кирку и лопату" crafts both.
 */
public final class VoiceParser {
    private static final Set<Kind> VERBS = EnumSet.of(Kind.START, Kind.STOP, Kind.MOVE, Kind.RUN, Kind.SLOW, Kind.JUMP,
        Kind.SNEAK, Kind.STAND, Kind.TURN, Kind.AROUND, Kind.LOOK, Kind.HIT, Kind.USE, Kind.OPEN,
        Kind.PLACE, Kind.EAT, Kind.CLOSE, Kind.TAKE, Kind.CRAFT, Kind.DROP, Kind.SEED, Kind.UPGRADE,
        Kind.CLEAR, Kind.SKIP, Kind.REPEAT, Kind.APPROACH, Kind.PICKUP, Kind.SWIM, Kind.SURFACE, Kind.DIVE, Kind.EXIT);
    /** Verbs that start a new command when they show up mid-phrase. */
    private static final Set<Kind> STRONG = EnumSet.of(Kind.START, Kind.STOP, Kind.HIT, Kind.USE, Kind.OPEN, Kind.PLACE,
        Kind.EAT, Kind.CLOSE, Kind.TAKE, Kind.CRAFT, Kind.DROP, Kind.SNEAK, Kind.STAND, Kind.SEED, Kind.UPGRADE,
        Kind.CLEAR, Kind.SKIP, Kind.PICKUP, Kind.SURFACE, Kind.DIVE, Kind.EXIT);
    /** Verbs whose objects can be listed: "скрафти кирку, лопату и топор". */
    private static final Set<Kind> INHERITABLE = EnumSet.of(Kind.CRAFT, Kind.TAKE, Kind.DROP, Kind.HIT, Kind.LOOK,
        Kind.PLACE, Kind.UPGRADE);
    private static final Kind[] PRIORITY = {Kind.STOP, Kind.SEED, Kind.START, Kind.CRAFT, Kind.DROP, Kind.EAT,
        Kind.PLACE, Kind.TAKE, Kind.CLEAR, Kind.SKIP, Kind.UPGRADE, Kind.EXIT, Kind.SURFACE, Kind.DIVE, Kind.CLOSE, Kind.OPEN, Kind.USE, Kind.HIT,
        Kind.PICKUP, Kind.APPROACH, Kind.SNEAK, Kind.STAND, Kind.LOOK, Kind.AROUND, Kind.TURN, Kind.JUMP, Kind.SWIM, Kind.RUN, Kind.SLOW, Kind.MOVE,
        Kind.REPEAT};

    private record Word(String text, Set<Tok> toks) {
        boolean has(Kind kind) {
            for (Tok tok : toks) if (tok.kind() == kind) return true;
            return false;
        }

        String value(Kind kind) {
            for (Tok tok : toks) if (tok.kind() == kind) return tok.value();
            return null;
        }

        boolean isVerb() {
            for (Tok tok : toks) if (VERBS.contains(tok.kind())) return true;
            return false;
        }

        boolean isStrongVerb() {
            for (Tok tok : toks) if (STRONG.contains(tok.kind())) return true;
            return false;
        }
    }

    private record ItemRef(String item, String tier, int count) {}

    private VoiceParser() {}

    public static List<VoiceCommand> parse(String text, String languageId) {
        VoiceLexicon lexicon = VoiceLexicon.forLanguage(languageId);
        List<Word> words = mergeNumbers(tokenize(text, lexicon, languageId));
        List<VoiceCommand> out = new ArrayList<>();
        Kind previousVerb = null;
        boolean stepContext = false;
        for (List<Word> segment : segments(words)) {
            Kind verb = verbOf(segment);
            // "шаг вперёд и влево": the second part is another step, not a turn.
            boolean bareDirection = (verb == null || verb == Kind.REPEAT) && (sideDir(segment) != null || any(segment, Kind.BACK) || any(segment, Kind.FWD));
            if (stepContext && bareDirection) verb = Kind.MOVE;
            stepContext = (stepContext && bareDirection) || any(segment, Kind.STEP);
            if (verb == null && previousVerb != null && INHERITABLE.contains(previousVerb)
                && (any(segment, Kind.ITEM) || any(segment, Kind.TARGET) || (previousVerb == Kind.UPGRADE && !unknown(segment).isEmpty()))) {
                verb = previousVerb;
            }
            if (verb == null) verb = infer(segment);
            int before = out.size();
            build(verb, segment, out, stepContext);
            if (verb != null && out.size() > before) previousVerb = verb;
            else if (verb != null) previousVerb = verb;
        }
        return out;
    }

    /** Words after which the speaker is clearly not done: "наведись на…", "иди к…", "и потом…". */
    private static final Map<String, Set<String>> DANGLING = Map.of(
        "ru", Set.of("на", "в", "во", "к", "ко", "до", "по", "за", "с", "со", "из", "у", "от", "для", "про", "через", "и", "а", "потом", "затем", "или", "не"),
        "uk", Set.of("на", "в", "у", "до", "по", "за", "з", "із", "зі", "від", "для", "через", "і", "й", "та", "а", "потім", "або", "не"),
        "en", Set.of("to", "on", "at", "in", "into", "the", "a", "an", "and", "then", "or", "of", "for", "from", "with", "not", "towards", "toward"));
    /** Verbs that need something after them ("наведись", "скрафти", "поставь"). */
    private static final Set<Kind> NEEDS_OBJECT = EnumSet.of(Kind.LOOK, Kind.CRAFT, Kind.PLACE, Kind.TAKE, Kind.DROP, Kind.TOWARD);
    private static final Set<Kind> OBJECTS = EnumSet.of(Kind.ITEM, Kind.TARGET, Kind.CARDINAL, Kind.UP, Kind.DOWN, Kind.LEVEL,
        Kind.REFINE, Kind.LEFT, Kind.RIGHT, Kind.FWD, Kind.BACK, Kind.NUMBER, Kind.ALL, Kind.SLOT, Kind.INVENTORY, Kind.UPGRADER, Kind.THAT, Kind.SEED);

    /**
     * Whether the phrase sounds cut off: it ends on a preposition or "и", or has a
     * verb that still waits for its object. Such a phrase is held for a moment and
     * joined with whatever comes next instead of being run half-heard.
     */
    public static boolean looksUnfinished(String text, String languageId) {
        String normalized = VoiceLexicon.normalize(text).trim();
        if (normalized.isEmpty()) return false;
        String[] raw = normalized.split("\\s+");
        String last = raw[raw.length - 1];
        if (DANGLING.getOrDefault(languageId, Set.of()).contains(last)) return true;
        VoiceLexicon lexicon = VoiceLexicon.forLanguage(languageId);
        List<Word> words = tokenize(text, lexicon, languageId);
        if (words.isEmpty()) return false;
        Word lastWord = words.get(words.size() - 1);
        if (lastWord.has(Kind.SEP) || lastWord.has(Kind.TOWARD) || lastWord.has(Kind.NOT)) return true;
        // Look at the last command only: "иди вперёд и наведись" waits for the target.
        List<List<Word>> parts = segments(words);
        List<Word> tail = parts.isEmpty() ? words : parts.get(parts.size() - 1);
        boolean needs = false, has = false;
        for (Word word : tail) {
            if (PRONOUNS.contains(word.text())) has = true;
            for (Tok tok : word.toks()) {
                if (NEEDS_OBJECT.contains(tok.kind())) needs = true;
                if (OBJECTS.contains(tok.kind())) has = true;
            }
        }
        if (needs && !has) return true;
        // A number with nothing after it: "иди пять", "поставь три".
        return lastWord.has(Kind.NUMBER) && !lastWord.has(Kind.BLOCKS);
    }

    /** Fast check used on partial results so "стоп" reacts before the phrase ends. */
    public static boolean containsStop(String text, String languageId) {
        VoiceLexicon lexicon = VoiceLexicon.forLanguage(languageId);
        boolean negated = false;
        for (Word word : tokenize(text, lexicon, languageId)) {
            if (word.has(Kind.NOT)) { negated = true; continue; }
            if (word.has(Kind.STOP) && !negated) return true;
            negated = false;
        }
        return false;
    }

    /** Fixed multi-word phrases rewritten to one known word before lookup. */
    private static final Map<String, String[][]> PHRASES = Map.of(
        "en", new String[][] {
            {"pick up", "pickup"}, {"pick it up", "pickup"}, {"pick them up", "pickup"},
            {"come closer", "approach"}, {"get closer", "approach"}, {"go closer", "approach"},
            {"walk up", "approach"}, {"go up to", "approach"}, {"come here", "approach"},
            {"hold space", "keep jumping"}, {"hold jump", "keep jumping"},
            {"get out of the water", "exitwater"}, {"get out of water", "exitwater"}, {"out of the water", "exitwater"}},
        "ru", new String[][] {{"поближе", "подойди чуть"}, {"ближе подойди", "подойди чуть"},
            {"присел по центру", "прицел по центру"}, {"присел пациенту", "прицел по центру"}, {"присел по центр", "прицел по центру"},
            {"на видеть", "наведи"}, {"на веди", "наведи"}, {"самих пробел", "зажми пробел"}, {"сам их пробел", "зажми пробел"}},
        "uk", new String[][] {{"ближче підійди", "підійди трохи"}});

    /** Whole phrases the model mishears for a short command; only when said alone. */
    private static final Map<String, Map<String, String>> SOLO = Map.of(
        "ru", Map.of("что", "стоп", "чё", "стоп", "би", "беги", "стоп ка", "стоп", "той", "стой"),
        "uk", Map.of("що", "стоп"),
        "en", Map.of("stuff", "stop", "stopped", "stop", "top", "stop"));

    private static final Set<String> PRONOUNS = Set.of("нему", "ней", "него", "неё", "нее", "ним", "нього", "неї", "них",
        "it", "him", "her", "them", "that", "this");

    /** Chatter that must never be fuzzily turned into a command word. */
    private static final Map<String, Set<String>> FILLER = Map.of(
        "ru", Set.of("короче", "короч", "коротко", "вообще", "ваще", "просто", "сейчас", "щас", "ладно", "блин",
            "значит", "кстати", "конечно", "типа", "ребята", "ребят", "чат", "чатик", "господи", "боже", "короче говоря",
            "наверное", "кажется", "понятно", "вроде", "смотрите", "ставка"),
        "uk", Set.of("короче", "взагалі", "просто", "зараз", "ладно", "блін", "значить", "типу", "мабуть", "здається"),
        "en", Set.of("like", "actually", "basically", "okay", "guys", "chat", "literally", "probably", "whatever"));

    private static List<Word> tokenize(String text, VoiceLexicon lexicon, String languageId) {
        List<Word> words = new ArrayList<>();
        String normalized = " " + VoiceLexicon.normalize(text) + " ";
        for (String[] phrase : PHRASES.getOrDefault(languageId, new String[0][])) {
            normalized = normalized.replace(" " + phrase[0] + " ", " " + phrase[1] + " ");
        }
        String solo = SOLO.getOrDefault(languageId, Map.of()).get(normalized.trim());
        if (solo != null) normalized = " " + solo + " ";
        Set<String> filler = FILLER.getOrDefault(languageId, Set.of());
        for (String raw : normalized.trim().split("\\s+")) {
            if (raw.isEmpty() || raw.equals("unk") || filler.contains(raw)) continue;
            words.add(new Word(raw, lexicon.lookup(raw)));
        }
        return words;
    }

    /** "сто восемьдесят" -> 180, "one hundred eighty" -> 180, "сорок пять" -> 45. */
    private static List<Word> mergeNumbers(List<Word> words) {
        List<Word> out = new ArrayList<>();
        int i = 0;
        while (i < words.size()) {
            Word word = words.get(i);
            boolean numeric = word.has(Kind.NUMBER) || word.has(Kind.MULT);
            if (!numeric) {
                out.add(word);
                i++;
                continue;
            }
            int total = 0, current = 0, lastPart = Integer.MAX_VALUE;
            int j = i;
            while (j < words.size()) {
                Word next = words.get(j);
                if (next.has(Kind.MULT)) {
                    current = (current == 0 ? 1 : current) * 100;
                    total += current;
                    current = 0;
                    lastPart = 100;
                    j++;
                    continue;
                }
                if (!next.has(Kind.NUMBER)) break;
                int value = Integer.parseInt(next.value(Kind.NUMBER));
                if (value >= lastPart && j > i) break; // "два три" are two numbers, not 5
                current += value;
                lastPart = value;
                j++;
            }
            total += current;
            Set<Tok> toks = new java.util.LinkedHashSet<>();
            toks.add(new Tok(Kind.NUMBER, String.valueOf(total)));
            out.add(new Word(String.valueOf(total), toks));
            i = Math.max(j, i + 1);
        }
        return out;
    }

    private static List<List<Word>> segments(List<Word> words) {
        List<List<Word>> segments = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        boolean strong = false;
        boolean anyVerb = false;
        for (Word word : words) {
            if (word.has(Kind.SEP) && word.toks().size() == 1) {
                if (!current.isEmpty()) segments.add(current);
                current = new ArrayList<>();
                strong = false;
                anyVerb = false;
                continue;
            }
            boolean startsNew = !current.isEmpty() && word.isVerb() && !word.has(Kind.REPEAT)
                && ((word.isStrongVerb() && anyVerb) || (strong && !word.has(Kind.FWD)));
            if (startsNew) {
                segments.add(current);
                current = new ArrayList<>();
                strong = false;
                anyVerb = false;
            }
            current.add(word);
            if (word.isVerb()) anyVerb = true;
            if (word.isStrongVerb()) strong = true;
        }
        if (!current.isEmpty()) segments.add(current);
        return segments;
    }

    private static Kind verbOf(List<Word> segment) {
        // "выйди из воды", "вылезь из воды", "плыви к берегу" all mean: get out of the water.
        if (any(segment, Kind.WATER) && (any(segment, Kind.CLOSE) || any(segment, Kind.EXIT) || any(segment, Kind.MOVE) || any(segment, Kind.SWIM))) return Kind.EXIT;
        for (Kind kind : PRIORITY) if (any(segment, kind)) return kind;
        return null;
    }

    private static Kind infer(List<Word> segment) {
        if (any(segment, Kind.UPGRADER) || any(segment, Kind.INVENTORY)) return Kind.OPEN;
        if (any(segment, Kind.STEP)) return Kind.MOVE;
        if (any(segment, Kind.SLOT)) return Kind.TAKE;
        if (any(segment, Kind.ITEM) && !any(segment, Kind.TARGET)) return Kind.ITEM;
        if (any(segment, Kind.TARGET) || any(segment, Kind.CARDINAL) || any(segment, Kind.REFINE)) return Kind.LOOK;
        if (any(segment, Kind.UP) || any(segment, Kind.DOWN) || any(segment, Kind.LEVEL)) return Kind.LOOK;
        if (any(segment, Kind.LEFT) || any(segment, Kind.RIGHT)) return any(segment, Kind.SIDE) ? Kind.MOVE : Kind.TURN;
        if (any(segment, Kind.FWD) || any(segment, Kind.BACK) || any(segment, Kind.SIDE)) return Kind.MOVE;
        if (any(segment, Kind.ITEM)) return Kind.ITEM;
        if (any(segment, Kind.NOT) && any(segment, Kind.THAT)) return Kind.LOOK;
        return null;
    }

    private static void build(Kind verb, List<Word> seg, List<VoiceCommand> out, boolean stepContext) {
        boolean negated = any(seg, Kind.NOT);
        int number = firstNumber(seg);
        boolean little = any(seg, Kind.LITTLE);

        // Upgrader odds work with or without a verb: "тридцать процентов", "икс четыре".
        if (number > 0 && (any(seg, Kind.PERCENT) || any(seg, Kind.TIMES)) && verb != Kind.CRAFT && verb != Kind.MOVE && verb != Kind.JUMP && !any(seg, Kind.REPEAT)) {
            double ratio = any(seg, Kind.PERCENT) ? 100.0 / Math.min(75, number) : Math.max(1, number);
            out.add(new VoiceCommand.UpgraderChance(ratio));
            if (verb == Kind.UPGRADE && !hasObject(seg)) out.add(new VoiceCommand.UpgraderRoll());
            return;
        }
        if (verb == null) return;
        // "сделай шаг влево": "сделай" is also a craft verb, but nothing to craft here.
        if (verb == Kind.CRAFT && items(seg).isEmpty() && (any(seg, Kind.STEP) || sideDir(seg) != null || any(seg, Kind.BACK))) {
            verb = Kind.MOVE;
        }
        switch (verb) {
            case STOP -> { if (!negated) out.add(new VoiceCommand.Stop()); }
            case START -> { if (!negated) out.add(new VoiceCommand.Start()); }
            case SEED -> { if (!negated) out.add(new VoiceCommand.NewSeed()); }
            case REPEAT -> out.add(new VoiceCommand.Repeat(Math.max(1, Math.min(20, number))));
            case SLOW -> out.add(new VoiceCommand.Sprint(false));
            case SNEAK -> out.add(new VoiceCommand.Sneak(!negated));
            case STAND -> out.add(new VoiceCommand.Sneak(false));
            case MOVE, RUN -> {
                if (negated) {
                    out.add(verb == Kind.RUN ? new VoiceCommand.Sprint(false) : new VoiceCommand.Stop());
                    return;
                }
                String toward = value(seg, Kind.TARGET);
                if (toward != null && (any(seg, Kind.TOWARD) || any(seg, Kind.APPROACH))) {
                    out.add(new VoiceCommand.Approach(toward, little));
                    return;
                }
                // "иди к нему", "go to it": walk to whatever was aimed at last.
                if (any(seg, Kind.TOWARD) && seg.stream().anyMatch(w -> PRONOUNS.contains(w.text()))) {
                    out.add(new VoiceCommand.Approach(null, little));
                    return;
                }
                buildMove(seg, verb == Kind.RUN || any(seg, Kind.RUN), number, little, out, stepContext);
                if (any(seg, Kind.JUMP)) out.add(new VoiceCommand.Jump(any(seg, Kind.ALWAYS), 1));
            }
            case JUMP -> {
                if (negated) {
                    out.add(new VoiceCommand.JumpOff());
                    return;
                }
                if (any(seg, Kind.FWD) || any(seg, Kind.BACK) || any(seg, Kind.RUN) || any(seg, Kind.MOVE)) {
                    buildMove(seg, any(seg, Kind.RUN), 0, little, out, false);
                }
                out.add(new VoiceCommand.Jump(any(seg, Kind.ALWAYS), Math.max(1, Math.min(number, 20))));
            }
            case SURFACE -> out.add(new VoiceCommand.Swim("up"));
            case DIVE -> out.add(new VoiceCommand.Swim("down"));
            case EXIT -> out.add(new VoiceCommand.Swim("out"));
            case SWIM -> {
                if (any(seg, Kind.UP)) out.add(new VoiceCommand.Swim("up"));
                else if (any(seg, Kind.DOWN)) out.add(new VoiceCommand.Swim("down"));
                else buildMove(seg, any(seg, Kind.RUN), number, little, out, stepContext);
            }
            case TURN, AROUND, LOOK -> buildLook(verb, seg, number, little, negated, out);
            case HIT -> {
                String refine = refine(seg, negated);
                if (refine != null && value(seg, Kind.TARGET) == null) {
                    out.add(new VoiceCommand.Refine(refine));
                    out.add(new VoiceCommand.Hit(null, false));
                } else {
                    out.add(new VoiceCommand.Hit(value(seg, Kind.TARGET), any(seg, Kind.ALL)));
                }
            }
            case USE -> out.add(new VoiceCommand.Use(value(seg, Kind.TARGET)));
            case APPROACH -> out.add(new VoiceCommand.Approach(value(seg, Kind.TARGET), little));
            case PICKUP -> out.add(new VoiceCommand.Pickup(any(seg, Kind.ALL)));
            case OPEN -> {
                if (any(seg, Kind.UPGRADER) || any(seg, Kind.UPGRADE)) out.add(new VoiceCommand.UpgraderOpen(!negated));
                else if (any(seg, Kind.INVENTORY)) out.add(new VoiceCommand.Inventory(!negated));
                else out.add(new VoiceCommand.Use(value(seg, Kind.TARGET)));
            }
            case CLOSE -> out.add(any(seg, Kind.UPGRADER) ? new VoiceCommand.UpgraderOpen(false) : new VoiceCommand.Inventory(false));
            case UPGRADE -> {
                if (any(seg, Kind.UPGRADER) && !hasObject(seg)) {
                    out.add(new VoiceCommand.UpgraderOpen(true));
                } else if (hasObject(seg)) {
                    List<ItemRef> items = items(seg);
                    ItemRef first = items.isEmpty() ? null : items.get(0);
                    out.add(new VoiceCommand.UpgraderTarget(first == null ? null : first.item, first == null ? null : first.tier,
                        value(seg, Kind.TARGET), unknown(seg), Math.max(1, number)));
                } else {
                    out.add(new VoiceCommand.UpgraderRoll());
                }
            }
            case CLEAR -> out.add(new VoiceCommand.UpgraderClear());
            case SKIP -> out.add(new VoiceCommand.UpgraderSkip());
            case PLACE -> {
                List<ItemRef> items = items(seg);
                ItemRef first = items.isEmpty() ? null : items.get(0);
                out.add(new VoiceCommand.Place(first == null ? null : first.item, first == null ? null : first.tier,
                    value(seg, Kind.TARGET), Math.max(0, number), unknown(seg)));
            }
            case EAT -> out.add(new VoiceCommand.Eat());
            case TAKE -> {
                List<ItemRef> items = items(seg);
                if (!items.isEmpty()) {
                    out.add(new VoiceCommand.Select(items.get(0).item, items.get(0).tier, 0));
                } else if (number >= 1 && number <= 9) {
                    out.add(new VoiceCommand.Select(null, null, number));
                }
            }
            case CRAFT -> {
                boolean all = any(seg, Kind.ALL);
                for (ItemRef ref : items(seg)) {
                    out.add(new VoiceCommand.Craft(ref.item, ref.tier, Math.max(1, Math.min(64, ref.count)), all));
                }
            }
            case ITEM -> {
                for (ItemRef ref : items(seg)) out.add(new VoiceCommand.Get(ref.item, ref.tier, Math.max(1, Math.min(64, ref.count))));
            }
            case DROP -> {
                List<ItemRef> items = items(seg);
                out.add(new VoiceCommand.Drop(items.isEmpty() ? null : items.get(0).item, any(seg, Kind.ALL)));
            }
            default -> {}
        }
    }

    private static boolean hasObject(List<Word> seg) {
        return any(seg, Kind.ITEM) || any(seg, Kind.TARGET) || !unknown(seg).isEmpty();
    }

    /** Direction of "выше / левее / другой / не тот", or null. */
    private static String refine(List<Word> seg, boolean negated) {
        String value = value(seg, Kind.REFINE);
        if (value != null) return value;
        return negated && any(seg, Kind.THAT) ? "other" : null;
    }

    private static void buildMove(List<Word> seg, boolean sprint, int number, boolean little, List<VoiceCommand> out, boolean stepContext) {
        boolean degrees = any(seg, Kind.DEGREE);
        boolean step = any(seg, Kind.STEP) || stepContext;
        int blocks = (any(seg, Kind.BLOCKS) || step || (number > 0 && !degrees)) ? Math.min(Math.max(number, step ? 1 : 0), 200) : 0;
        Dir side = sideDir(seg);
        // "шаг влево" and "вбок" strafe; "иди налево" turns and walks.
        if (any(seg, Kind.SIDE) || (step && side != null)) {
            out.add(new VoiceCommand.Move(side == null ? Dir.LEFT : side, sprint, blocks));
            return;
        }
        if (side != null) {
            float angle = degrees && number > 0 ? number : little ? 20f : 90f;
            out.add(new VoiceCommand.Turn(side == Dir.LEFT ? -angle : angle));
            out.add(new VoiceCommand.Move(Dir.FORWARD, sprint, blocks));
            return;
        }
        out.add(new VoiceCommand.Move(any(seg, Kind.BACK) ? Dir.BACK : Dir.FORWARD, sprint, blocks));
    }

    private static void buildLook(Kind verb, List<Word> seg, int number, boolean little, boolean negated, List<VoiceCommand> out) {
        String target = value(seg, Kind.TARGET);
        String cardinal = value(seg, Kind.CARDINAL);
        String refine = refine(seg, negated);
        Dir side = sideDir(seg);
        if (verb == Kind.AROUND || (verb == Kind.TURN && any(seg, Kind.BACK) && side == null && target == null)) {
            out.add(new VoiceCommand.Turn(180f));
            return;
        }
        if (target != null && (verb != Kind.TURN || side == null)) {
            out.add(new VoiceCommand.Aim(target));
            if (refine != null) out.add(new VoiceCommand.Refine(refine));
            return;
        }
        if (refine != null) {
            out.add(new VoiceCommand.Refine(refine));
            return;
        }
        if (cardinal != null) {
            out.add(new VoiceCommand.Face(VoiceCommand.Cardinal.valueOf(cardinal.toUpperCase(java.util.Locale.ROOT))));
            return;
        }
        if (any(seg, Kind.UP)) {
            out.add(little ? new VoiceCommand.Pitch(-15f, true) : number > 0 ? new VoiceCommand.Pitch(-number, true) : new VoiceCommand.Pitch(-70f, false));
            return;
        }
        if (any(seg, Kind.DOWN)) {
            out.add(little ? new VoiceCommand.Pitch(15f, true) : number > 0 ? new VoiceCommand.Pitch(number, true) : new VoiceCommand.Pitch(75f, false));
            return;
        }
        if (side != null) {
            float angle = number > 0 ? number : little ? 20f : 90f;
            out.add(new VoiceCommand.Turn(side == Dir.LEFT ? -angle : angle));
            return;
        }
        if (any(seg, Kind.LEVEL)) {
            out.add(new VoiceCommand.Pitch(0f, false));
            return;
        }
        if (verb == Kind.TURN && number > 0) out.add(new VoiceCommand.Turn(number));
    }

    private static Dir sideDir(List<Word> seg) {
        for (Word word : seg) {
            if (word.has(Kind.LEFT)) return Dir.LEFT;
            if (word.has(Kind.RIGHT)) return Dir.RIGHT;
        }
        return null;
    }

    private static List<ItemRef> items(List<Word> seg) {
        List<ItemRef> out = new ArrayList<>();
        String tier = null;
        int count = 0;
        for (Word word : seg) {
            if (word.has(Kind.NUMBER)) count = Integer.parseInt(word.value(Kind.NUMBER));
            String wordTier = word.value(Kind.TIER);
            String item = word.value(Kind.ITEM);
            if (item != null) {
                if (!out.isEmpty() && out.get(out.size() - 1).item.equals(item) && tier == null && count == 0) continue;
                out.add(new ItemRef(item, tier, count));
                tier = null;
                count = 0;
            } else if (wordTier != null) {
                tier = wordTier;
            }
        }
        return out;
    }

    /** Words the lexicon does not know, kept for matching item names ("апгрейд на тотем"). */
    private static String unknown(List<Word> seg) {
        StringBuilder out = new StringBuilder();
        for (Word word : seg) {
            if (!word.toks().isEmpty() || word.text().length() < 3) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.text());
        }
        return out.toString();
    }

    private static boolean any(List<Word> seg, Kind kind) {
        for (Word word : seg) if (word.has(kind)) return true;
        return false;
    }

    private static String value(List<Word> seg, Kind kind) {
        for (Word word : seg) {
            String value = word.value(kind);
            if (value != null) return value;
        }
        return null;
    }

    private static int firstNumber(List<Word> seg) {
        for (Word word : seg) if (word.has(Kind.NUMBER)) return Integer.parseInt(word.value(Kind.NUMBER));
        return 0;
    }
}
