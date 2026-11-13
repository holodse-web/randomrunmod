package com.randomrun.features.voice.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Word lists for every language. A word ending in {@code *} is a stem and
 * matches any word that starts with it, which covers Russian and Ukrainian
 * endings ("кирка", "кирку", "кирки"). Exact words win over stems and longer
 * stems win over shorter ones, so "деревянную" is a tier, not a tree.
 * One word may carry several meanings; the parser picks by context.
 */
public final class VoiceLexicon {
    public enum Kind {
        STOP, MOVE, FWD, BACK, LEFT, RIGHT, SIDE, RUN, SLOW, JUMP, ALWAYS, SNEAK, STAND,
        TURN, AROUND, LITTLE, DEGREE, BLOCKS, UP, DOWN, LEVEL, LOOK, CARDINAL,
        HIT, USE, OPEN, PLACE, EAT, INVENTORY, CLOSE, TAKE, SLOT, CRAFT, DROP,
        SEP, ALL, NOT, NUMBER, MULT, ITEM, TIER, TARGET, START,
        REFINE, THAT, STEP, REPEAT, SEED, UPGRADER, UPGRADE, PERCENT, TIMES, PIECES, CLEAR, SKIP,
        APPROACH, TOWARD, PICKUP, SWIM, SURFACE, DIVE, EXIT, WATER
    }

    public record Tok(Kind kind, String value) {
        public int number() {
            try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
        }
    }

    private static final String RU = """
        START = старт погнали поехали начали го
        STOP = стоп стой* хватит замри останов* тормоз* прекрат* отмен* харэ чтоб штоп штопор стопе стопэ стопа
        MOVE = иди идти идем идём пойди пошли пошел пошёл шагай шагни двигай* топай ходи вперед перед
        FWD = вперед вперёд прямо
        BACK = назад
        LEFT = налево влево лево левой минус
        RIGHT = направо вправо право правой плюс
        LITTLE = чуть немного немножко слегка капельку чутка чуток
        REFINE:up = выше повыше
        REFINE:down = ниже пониже
        REFINE:left = левее* полевее левей леви
        REFINE:right = правее* поправее правей
        REFINE:far = дальше подальше
        REFINE:near = ближе поближе
        REFINE:other = другой другую другое другая другие следующ* соседн*
        THAT = ту та то тот туда
        STEP = шаг шага шагов шагни шажок шажочек
        REPEAT = еще ещё повтори* снова опять заново
        SEED = сид сида сиды сидом
        UPGRADER = апгрейдер*
        UPGRADE = улучш* апгрейд апгрейдни* крути крутани* прокрути* ролл* рольни*
        PERCENT = процент* проц
        TIMES = икс иксов раза умнож*
        PIECES = штук* штучк*
        CLEAR = очист* сброс* сбрось убери* обнули*
        SKIP = пропусти* скип*
        SIDE = вбок боком стрейф* приставн*
        RUN = беги бежать бегом бег спринт* быстрее быстро
        SLOW = медленн* тише шагом помедленнее
        JUMP = прыг* прыж* подпрыг* скачи скакать пробел
        ALWAYS = постоянно всегда непрерывно время зажм* зажать
        SWIM = плыви плыть плавай поплыли поплыви плыву плывем плывём
        SURFACE = всплыв* вынырн* выныр* выплыв* всплыл*
        DIVE = ныря* нырн* ныряй тони тонуть утони погруз* дно
        EXIT = вылез* вылаз* выберись выбраться выползи* вылези
        WATER = вода воды воде воду берег*
        SNEAK = присядь присед* крадись красться шифт сядь пригнись
        STAND = встань вставай поднимись выпрямись
        TURN = поверн* поворот* крутись крутани*
        AROUND = разверн* разворот* кругом оберн*
        DEGREE = градус*
        BLOCKS = блок блока блоков блоки клетк* клеток метр*
        UP = вверх наверх небо верх
        DOWN = вниз ноги пол
        LEVEL = прямо ровно горизонт* центр* середин* посередин* пациенту цедру
        LOOK = смотри посмотри глянь взгляни гляди наведи* наводи* доведи* целься прицел* найди ищи
        APPROACH = подойди* подойти подход* подбег* подбеж* приблиз* подъедь*
        TOWARD = к ко
        PICKUP = подбери* подбирай* подними* поднять забери* собери собирай побери* лут* залут*
        CARDINAL:north = север*
        CARDINAL:south = юг юга югу
        CARDINAL:west = запад*
        CARDINAL:east = восток*
        HIT = лома* слома* разлома* руби сруби рубить рубани* копа* выкопа* раскопа* добыва* добуд* долби* бей бить ударь удар* атак* убей убить мочи круши разбей
        USE = используй юзай нажми кликни тыкни жми активир*
        OPEN = открой открыть откр*
        PLACE = постав* ставь положи клади размести
        EAT = ешь съешь кушай покушай поешь жри пожри пей выпей
        INVENTORY = инвентар* рюкзак* сумк*
        CLOSE = закрой закрыть выйди выход
        TAKE = возьми бери взять выбери достань держи экипир* переключи* смени
        SLOT = слот* ячейк*
        CRAFT = скрафт* крафт* сделай сделать создай смастери изготов*
        DROP = выброс* выкин* брось бросай дропни
        SEP = и потом затем после а далее
        ALL = все всё всю весь максимум всех
        NOT = не
        ITEM:pickaxe = кирк* кайл*
        ITEM:axe = топор*
        ITEM:shovel = лопат*
        ITEM:sword = меч меча мечи мечом мечик
        ITEM:hoe = мотыг*
        ITEM:crafting_table = верстак*
        ITEM:stick = палк* палоч* палок
        ITEM:planks = доск* досок
        ITEM:furnace = печ* печь
        ITEM:chest = сундук*
        ITEM:torch = факел*
        ITEM:bucket = ведр* ведер
        ITEM:boat = лодк* лодок
        ITEM:bed = кроват*
        ITEM:ladder = лестниц*
        ITEM:bread = хлеб*
        ITEM:shield = щит*
        ITEM:bow = лук лука луком
        ITEM:arrow = стрел*
        ITEM:flint_and_steel = огнив* зажигалк*
        ITEM:helmet = шлем*
        ITEM:chestplate = нагрудник* кирас*
        ITEM:leggings = понож* штан*
        ITEM:boots = ботин* сапог*
        ITEM:food = еда еду еды едой
        ITEM:emerald = изумруд*
        ITEM:redstone = редстоун* редстон*
        ITEM:lapis_lazuli = лазурит*
        ITEM:obsidian = обсидиан*
        ITEM:ender_pearl = жемчуг* перл*
        ITEM:blaze_rod = стерж* стержн*
        ITEM:netherite_ingot = незеритов*
        ITEM:gunpowder = порох*
        ITEM:string = нит* нитк*
        ITEM:bone = кост*
        ITEM:apple = яблок*
        ITEM:wool = шерст*
        TIER:wooden = деревян*
        TIER:stone = каменн*
        TIER:iron = железн*
        TIER:diamond = алмазн*
        TIER:golden = золот*
        TIER:copper = медн*
        TIER:netherite = незерит*
        TARGET:tree = дерев* бревн* древес* ствол*
        TARGET:leaves = листв* листь*
        TARGET:stone = камен* камн* камень булыж*
        TARGET:dirt = земл* грязь грунт* трава траву травы дерн*
        TARGET:sand = песок песк*
        TARGET:gravel = грави*
        TARGET:coal = угол уголь угл*
        TARGET:iron_ore = желез*
        TARGET:diamond_ore = алмаз*
        TARGET:gold_ore = золот*
        TARGET:water = вод*
        TARGET:crafting_table = верстак*
        TARGET:furnace = печ* печь
        TARGET:chest = сундук*
        TARGET:bed = кроват*
        TARGET:door = двер*
        TARGET:trapdoor = люк люка люки
        TARGET:item = предмет* дроп* вещ*
        TARGET:cow = коров*
        TARGET:pig = свин*
        TARGET:sheep = овц* овеч* барашк* баран*
        TARGET:chicken = кур кура куры куру куриц* курочк*
        TARGET:zombie = зомб*
        TARGET:skeleton = скелет*
        TARGET:creeper = крипер*
        TARGET:spider = паук*
        TARGET:enderman = эндер*
        TARGET:villager = жител*
        TARGET:hostile = моб* монстр* враг*
        TARGET:player = игрок* противник* соперник* оппонент*
        TARGET:animal = животн*
        NUMBER:0 = ноль нуль
        NUMBER:1 = один одну одна одно
        NUMBER:2 = два две пару дважды
        NUMBER:3 = три несколько трижды
        NUMBER:4 = четыре
        NUMBER:5 = пять
        NUMBER:6 = шесть
        NUMBER:7 = семь
        NUMBER:8 = восемь
        NUMBER:9 = девять
        NUMBER:10 = десять десяток
        NUMBER:11 = одиннадцать
        NUMBER:12 = двенадцать
        NUMBER:13 = тринадцать
        NUMBER:14 = четырнадцать
        NUMBER:15 = пятнадцать
        NUMBER:16 = шестнадцать
        NUMBER:17 = семнадцать
        NUMBER:18 = восемнадцать
        NUMBER:19 = девятнадцать
        NUMBER:20 = двадцать
        NUMBER:30 = тридцать
        NUMBER:40 = сорок
        NUMBER:45 = полоборота
        NUMBER:50 = пятьдесят
        NUMBER:60 = шестьдесят
        NUMBER:70 = семьдесят
        NUMBER:80 = восемьдесят
        NUMBER:90 = девяносто
        NUMBER:100 = сто сотня
        NUMBER:200 = двести
        NUMBER:300 = триста
        """;

    private static final String UK = """
        START = старт погнали поїхали почали го
        STOP = стоп стій зупин* досить годі замри скасу* припин*
        MOVE = йди іди йти іти піди підемо ходи крокуй рухай* вперед уперед
        FWD = вперед уперед прямо
        BACK = назад
        LEFT = ліворуч вліво уліво наліво ліво лівої
        RIGHT = праворуч вправо управо направо право правої
        LITTLE = трохи трішки злегка чуть трошки троха
        REFINE:up = вище
        REFINE:down = нижче
        REFINE:left = лівіше*
        REFINE:right = правіше*
        REFINE:far = далі подалі
        REFINE:near = ближче
        REFINE:other = інш* наступн* сусідн*
        THAT = ту та те той туди
        STEP = крок кроку кроки кроків крокни
        REPEAT = ще повтори* знову знов
        SEED = сід сіда сид сида
        UPGRADER = апгрейдер*
        UPGRADE = покращ* апгрейд апгрейдни* крути прокрути* ролл*
        PERCENT = відсот* процент*
        TIMES = ікс рази разів помнож*
        PIECES = штук*
        CLEAR = очист* обнули* прибери*
        SKIP = пропусти* скіп*
        SIDE = вбік боком стрейф*
        RUN = біжи бігом біг бігти спринт* швидше швидко
        SLOW = повільн* кроком тихіше
        JUMP = стриб* підстриб* скачи скакати пробіл
        ALWAYS = постійно завжди час затисн*
        SWIM = пливи плисти плавай попливли
        SURFACE = сплив* виплив* виринь* вирни* виринай
        DIVE = пірн* тони тонути занур* дно
        EXIT = вилізь* вилазь* виберись вибратися
        WATER = вода води воді воду берег*
        SNEAK = присядь присід* крадься шифт пригнись сядь
        STAND = встань вставай підведись піднімись
        TURN = поверн* поворот* крутись
        AROUND = розверн* розворот* кругом оберн* обернись
        DEGREE = градус*
        BLOCKS = блок блоки блоків блока клітин* метр*
        UP = вгору догори вверх угору небо нагору
        DOWN = вниз донизу униз ноги підлог*
        LEVEL = прямо рівно горизонт* центр* середин* посередин*
        LOOK = дивись подивись глянь гляди наведи* наводи* доведи* цілься приціл* знайди шукай
        APPROACH = підійди* підійти підход* підбіж* наблиз*
        TOWARD = до
        PICKUP = підбери* підбирай* підніми* забери* збери* лут*
        CARDINAL:north = північ*
        CARDINAL:south = південь півдня південн*
        CARDINAL:west = захід заходу західн*
        CARDINAL:east = схід сходу східн*
        HIT = лама* злама* руба* зруба* рубай рубани* копа* викопа* розкопа* добува* добудь довби* бий бити вдар* удар* атак* вбий вбити розбий круши
        USE = використ* натисни тисни клікни юзай активуй
        OPEN = відкрий відкрити відчини
        PLACE = постав* став поклади клади розмісти
        EAT = їж з'їж поїж їсти пий випий жери хавай
        INVENTORY = інвентар* рюкзак* сумк*
        CLOSE = закрий закрити вийди зачини
        TAKE = візьми бери взяти обери вибери дістань тримай екіпіруй перемкни* зміни
        SLOT = слот* комірк*
        CRAFT = скрафт* крафт* зроби зробити створи змайструй виготов*
        DROP = викинь викидай кинь скинь дропни
        SEP = і й та потім а після
        ALL = все всі усі усе всю максимум весь
        NOT = не
        ITEM:pickaxe = кирк* кайл*
        ITEM:axe = сокир*
        ITEM:shovel = лопат*
        ITEM:sword = меч меча мечі мечем
        ITEM:hoe = мотик* сапк*
        ITEM:crafting_table = верстак*
        ITEM:stick = палк* паличк* палиц* палок
        ITEM:planks = дошк* дощок
        ITEM:furnace = піч печі пічку печ*
        ITEM:chest = скрин*
        ITEM:torch = смолоскип* факел*
        ITEM:bucket = відр* відер
        ITEM:boat = човен човн*
        ITEM:bed = ліжк*
        ITEM:ladder = драбин*
        ITEM:bread = хліб*
        ITEM:shield = щит*
        ITEM:bow = лук лука
        ITEM:arrow = стріл*
        ITEM:flint_and_steel = кресал* запальничк*
        ITEM:helmet = шолом*
        ITEM:chestplate = нагрудник* кірас*
        ITEM:leggings = понож* штан*
        ITEM:boots = чобот*
        ITEM:food = їжа їжу їжі
        ITEM:emerald = смарагд* ізумруд*
        ITEM:redstone = редстоун* редстон*
        ITEM:lapis_lazuli = лазурит*
        ITEM:obsidian = обсидіан*
        ITEM:ender_pearl = перлин* перл*
        ITEM:blaze_rod = стрижн* стрижен*
        ITEM:gunpowder = порох*
        ITEM:string = нит* нитк*
        ITEM:bone = кіст* кістк*
        ITEM:apple = яблук*
        ITEM:wool = вовн*
        TIER:wooden = дерев'ян*
        TIER:stone = кам'ян*
        TIER:iron = залізн*
        TIER:diamond = алмазн*
        TIER:golden = золот*
        TIER:copper = мідн*
        TIER:netherite = незерит*
        TARGET:tree = дерев* колод* стовбур*
        TARGET:leaves = листя листок листв*
        TARGET:stone = камін* камен* бруківк*
        TARGET:dirt = земл* ґрунт* грунт* трава траву трави
        TARGET:sand = пісок піск*
        TARGET:gravel = гравій*
        TARGET:coal = вугіл* вугл*
        TARGET:iron_ore = залізо заліза залізом
        TARGET:diamond_ore = алмаз*
        TARGET:gold_ore = золот*
        TARGET:water = вод*
        TARGET:crafting_table = верстак*
        TARGET:furnace = піч печі пічку печ*
        TARGET:chest = скрин*
        TARGET:bed = ліжк*
        TARGET:door = двер*
        TARGET:trapdoor = люк люка люки
        TARGET:item = предмет* дроп* реч*
        TARGET:cow = коров*
        TARGET:pig = свин*
        TARGET:sheep = вівц* овеч* баран*
        TARGET:chicken = кур кури курк* курч*
        TARGET:zombie = зомб*
        TARGET:skeleton = скелет*
        TARGET:creeper = кріпер* крипер*
        TARGET:spider = павук*
        TARGET:enderman = ендер*
        TARGET:villager = жител* селян*
        TARGET:hostile = моб* монстр* ворог*
        TARGET:player = гравц* гравець суперник* противник* опонент*
        TARGET:animal = тварин*
        NUMBER:0 = нуль
        NUMBER:1 = один одну одна одне
        NUMBER:2 = два дві пару двічі
        NUMBER:3 = три кілька декілька тричі
        NUMBER:4 = чотири
        NUMBER:5 = п'ять
        NUMBER:6 = шість
        NUMBER:7 = сім
        NUMBER:8 = вісім
        NUMBER:9 = дев'ять
        NUMBER:10 = десять
        NUMBER:11 = одинадцять
        NUMBER:12 = дванадцять
        NUMBER:13 = тринадцять
        NUMBER:14 = чотирнадцять
        NUMBER:15 = п'ятнадцять
        NUMBER:16 = шістнадцять
        NUMBER:17 = сімнадцять
        NUMBER:18 = вісімнадцять
        NUMBER:19 = дев'ятнадцять
        NUMBER:20 = двадцять
        NUMBER:30 = тридцять
        NUMBER:40 = сорок
        NUMBER:50 = п'ятдесят
        NUMBER:60 = шістдесят
        NUMBER:70 = сімдесят
        NUMBER:80 = вісімдесят
        NUMBER:90 = дев'яносто
        NUMBER:100 = сто сотня
        NUMBER:200 = двісті
        NUMBER:300 = триста
        """;

    private static final String EN = """
        START = start begin
        STOP = stop halt freeze enough cancel wait abort
        MOVE = go walk move ahead onward onwards
        FWD = forward forwards ahead straight
        BACK = back backward backwards reverse
        LEFT = left
        RIGHT = right
        REFINE:up = higher
        REFINE:down = lower
        REFINE:far = further farther
        REFINE:near = closer nearer
        REFINE:other = another other next different
        THAT = that this
        STEP = step steps
        REPEAT = again repeat more
        SEED = seed seeds
        UPGRADER = upgrader
        UPGRADE = upgrade roll spin gamble
        PERCENT = percent* per
        TIMES = times x
        PIECES = pieces piece
        CLEAR = clear reset
        SKIP = skip
        SIDE = strafe* sideways sidestep
        RUN = run sprint* faster fast dash
        SLOW = slow slower slowly
        JUMP = jump* hop leap space
        ALWAYS = always keep constantly continuously nonstop
        SWIM = swim swimming
        SURFACE = surface* float*
        DIVE = dive* sink* drown*
        EXIT = exitwater
        WATER = water shore land
        SNEAK = sneak* crouch* shift duck
        STAND = stand unsneak uncrouch
        TURN = turn* rotate* spin
        AROUND = around about turnaround
        LITTLE = little bit slightly tad
        DEGREE = degree*
        BLOCKS = block blocks meter* metre*
        UP = up upward upwards sky
        DOWN = down downward downwards feet floor
        LEVEL = straight level horizon* center centre middle
        LOOK = look* aim* target find face point watch see
        APPROACH = approach* closer
        TOWARD = to towards toward
        PICKUP = collect* loot* pickup gather*
        CARDINAL:north = north*
        CARDINAL:south = south*
        CARDINAL:west = west*
        CARDINAL:east = east*
        HIT = break* mine mining dig* chop* hit* attack* kill* punch* smash* destroy* cut
        USE = use press click activate interact
        OPEN = open*
        PLACE = place* put set
        EAT = eat* drink* consume munch
        INVENTORY = inventory bag backpack
        CLOSE = close* exit quit
        TAKE = take select* equip* hold grab switch wield
        SLOT = slot*
        CRAFT = craft craft craft* make build create forge
        DROP = drop* throw* toss* discard
        SEP = and then after also
        ALL = all everything max maximum every
        NOT = not dont don't
        MULT = hundred
        ITEM:pickaxe = pickaxe* pick picks
        ITEM:axe = axe axes hatchet
        ITEM:shovel = shovel* spade*
        ITEM:sword = sword*
        ITEM:hoe = hoe hoes
        ITEM:crafting_table = table tables workbench* crafting
        ITEM:stick = stick*
        ITEM:planks = plank* boards
        ITEM:furnace = furnace* oven
        ITEM:chest = chest chests
        ITEM:torch = torch*
        ITEM:bucket = bucket*
        ITEM:boat = boat*
        ITEM:bed = bed beds
        ITEM:ladder = ladder*
        ITEM:bread = bread
        ITEM:shield = shield*
        ITEM:bow = bow bows
        ITEM:arrow = arrow*
        ITEM:flint_and_steel = flint lighter
        ITEM:helmet = helmet*
        ITEM:chestplate = chestplate*
        ITEM:leggings = leggings pants trousers
        ITEM:boots = boots
        ITEM:food = food
        ITEM:emerald = emerald*
        ITEM:redstone = redstone
        ITEM:lapis_lazuli = lapis
        ITEM:obsidian = obsidian
        ITEM:ender_pearl = pearl* enderpearl*
        ITEM:blaze_rod = rod rods
        ITEM:gunpowder = gunpowder powder
        ITEM:string = string strings
        ITEM:bone = bone bones
        ITEM:apple = apple apples
        ITEM:wool = wool
        TIER:wooden = wooden wood
        TIER:stone = stone
        TIER:iron = iron
        TIER:diamond = diamond*
        TIER:golden = gold golden
        TIER:copper = copper
        TIER:netherite = netherite
        TARGET:tree = tree* log logs trunk* wood
        TARGET:leaves = leaves leaf
        TARGET:stone = stone rock* cobble*
        TARGET:dirt = dirt ground grass soil
        TARGET:sand = sand
        TARGET:gravel = gravel
        TARGET:coal = coal
        TARGET:iron_ore = iron
        TARGET:diamond_ore = diamond*
        TARGET:gold_ore = gold
        TARGET:water = water
        TARGET:crafting_table = table workbench* crafting
        TARGET:furnace = furnace* oven
        TARGET:chest = chest chests
        TARGET:bed = bed beds
        TARGET:door = door doors
        TARGET:trapdoor = trapdoor*
        TARGET:item = item items drop drops
        TARGET:cow = cow*
        TARGET:pig = pig*
        TARGET:sheep = sheep
        TARGET:chicken = chicken*
        TARGET:zombie = zombie*
        TARGET:skeleton = skeleton*
        TARGET:creeper = creeper*
        TARGET:spider = spider*
        TARGET:enderman = enderm*
        TARGET:villager = villager*
        TARGET:hostile = mob mobs monster* enemy enemies hostile
        TARGET:player = player* opponent* rival*
        TARGET:animal = animal*
        NUMBER:0 = zero
        NUMBER:1 = one a once
        NUMBER:2 = two twice couple
        NUMBER:3 = three several
        NUMBER:4 = four
        NUMBER:5 = five
        NUMBER:6 = six
        NUMBER:7 = seven
        NUMBER:8 = eight
        NUMBER:9 = nine
        NUMBER:10 = ten
        NUMBER:11 = eleven
        NUMBER:12 = twelve
        NUMBER:13 = thirteen
        NUMBER:14 = fourteen
        NUMBER:15 = fifteen
        NUMBER:16 = sixteen
        NUMBER:17 = seventeen
        NUMBER:18 = eighteen
        NUMBER:19 = nineteen
        NUMBER:20 = twenty
        NUMBER:30 = thirty
        NUMBER:40 = forty
        NUMBER:50 = fifty
        NUMBER:60 = sixty
        NUMBER:70 = seventy
        NUMBER:80 = eighty
        NUMBER:90 = ninety
        """;

    private record Entry(String form, boolean stem, Tok tok) {}

    private static final String[] RU_ENDINGS = {"", "а", "у", "и", "ы", "е", "ой", "ом", "ами", "ах", "ов", "ей",
        "ь", "я", "ю", "ям", "ями", "ях", "ку", "ки", "ка", "ок", "й", "ай", "айте", "ите", "ить", "ать", "ять",
        "ешь", "ет", "ем", "ую", "ый", "ая", "ое", "ые", "ых", "ым", "ий", "яя", "ее", "ие", "ни", "нь", "ти",
        "ись", "ся", "сь", "ьте", "ег", "ит", "ло", "ла", "ли", "ул", "уй", "яй", "ей", "ью"};
    private static final String[] EN_ENDINGS = {"", "s", "es", "ing", "ed", "er", "e", "ward", "wards"};

    private static final Map<String, VoiceLexicon> CACHE = new HashMap<>();

    private final Map<String, List<Tok>> exact = new HashMap<>();
    private final List<Entry> stems = new ArrayList<>();
    /** Spelling as the speech model writes it (with ё and apostrophes), for the grammar. */
    private final Set<String> rawWords = new java.util.TreeSet<>();
    private final Set<String> rawStems = new java.util.TreeSet<>();

    private VoiceLexicon(String table) {
        for (String tableLine : table.split("\n")) {
            String line = tableLine.strip();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            String head = line.substring(0, eq).strip();
            String kindName = head, value = null;
            int colon = head.indexOf(':');
            if (colon >= 0) {
                kindName = head.substring(0, colon);
                value = head.substring(colon + 1);
            }
            Tok tok = new Tok(Kind.valueOf(kindName), value);
            for (String word : line.substring(eq + 1).strip().split("\\s+")) {
                String form = normalize(word.replace("*", ""));
                if (form.isEmpty()) continue;
                String spelled = word.replace("*", "").toLowerCase(Locale.ROOT);
                if (word.endsWith("*")) rawStems.add(spelled); else rawWords.add(spelled);
                if (word.endsWith("*")) {
                    stems.add(new Entry(form, true, tok));
                } else {
                    List<Tok> list = exact.computeIfAbsent(form, k -> new ArrayList<>());
                    if (!list.contains(tok)) list.add(tok);
                }
            }
        }
    }

    /**
     * Vosk grammar: every word the parser understands, with stems spelled out in
     * their common forms. Words the model does not know are skipped by Vosk;
     * "[unk]" soaks up unrelated speech so chatter does not turn into commands.
     */
    public String grammarJson(String languageId) {
        Set<String> words = new java.util.TreeSet<>(rawWords);
        String[] endings = "en".equals(languageId) ? EN_ENDINGS : RU_ENDINGS;
        for (String stem : rawStems) {
            for (String ending : endings) words.add(stem + ending);
        }
        words.removeIf(w -> w.isBlank() || w.chars().anyMatch(Character::isDigit));
        StringBuilder json = new StringBuilder("[");
        for (String word : words) {
            json.append('"').append(word.replace("\"", "")).append("\",");
        }
        json.append("\"[unk]\"]");
        return json.toString();
    }

    public static synchronized VoiceLexicon forLanguage(String languageId) {
        String key = languageId == null ? "ru" : languageId;
        return CACHE.computeIfAbsent(key, k -> new VoiceLexicon(switch (k) {
            case "uk" -> UK;
            case "en" -> EN;
            default -> RU;
        }));
    }

    /** All meanings of a word. Exact forms first; otherwise every meaning of the longest matching stem. */
    public Set<Tok> lookup(String word) {
        Set<Tok> out = new LinkedHashSet<>();
        if (word.isEmpty()) return out;
        if (word.chars().allMatch(Character::isDigit)) {
            out.add(new Tok(Kind.NUMBER, String.valueOf(Math.min(100000, Long.parseLong(word.length() > 6 ? "999999" : word)))));
            return out;
        }
        List<Tok> hit = exact.get(word);
        if (hit != null) out.addAll(hit);
        int best = 0;
        List<Tok> stemHits = new ArrayList<>();
        for (Entry entry : stems) {
            if (!word.startsWith(entry.form)) continue;
            int length = entry.form.length();
            if (length > best) {
                best = length;
                stemHits.clear();
            }
            if (length == best) stemHits.add(entry.tok);
        }
        // An exact match only blocks stems of the same kind ("давай" stays a verb, but
        // a stem may still add meanings the exact table does not know about).
        for (Tok tok : stemHits) {
            boolean shadowed = false;
            for (Tok existing : out) if (existing.kind == tok.kind) { shadowed = true; break; }
            if (!shadowed) out.add(tok);
        }
        if (out.isEmpty()) out.addAll(fuzzy(word));
        return out;
    }

    /**
     * Speech models often get one letter wrong ("верстах", "кирко"). For longer
     * words, accept a near miss of a known word or stem. Function words never
     * match fuzzily so chatter does not turn into "и" or "не".
     */
    private List<Tok> fuzzy(String word) {
        List<Tok> best = new ArrayList<>();
        if (word.length() < 6) return best;
        int limit = word.length() >= 9 ? 2 : 1;
        int bestDistance = limit + 1;
        for (Map.Entry<String, List<Tok>> entry : exact.entrySet()) {
            String form = entry.getKey();
            if (form.length() < 5 || form.charAt(0) != word.charAt(0) || Math.abs(form.length() - word.length()) > limit) continue;
            int d = distance(word, form, bestDistance);
            if (d > limit) continue;
            if (d < bestDistance) { bestDistance = d; best.clear(); }
            if (d == bestDistance) for (Tok tok : entry.getValue()) if (fuzzyAllowed(tok) && !best.contains(tok)) best.add(tok);
        }
        for (Entry entry : stems) {
            if (entry.form.length() < 5 || word.length() < entry.form.length() || word.length() > entry.form.length() + 3 || entry.form.charAt(0) != word.charAt(0)) continue;
            int d = distance(word.substring(0, entry.form.length()), entry.form, 2);
            if (d > 1) continue;
            if (d < bestDistance) { bestDistance = d; best.clear(); }
            if (d == bestDistance && fuzzyAllowed(entry.tok) && !best.contains(entry.tok)) best.add(entry.tok);
        }
        return best;
    }

    private static boolean fuzzyAllowed(Tok tok) {
        return switch (tok.kind) {
            case SEP, NOT, NUMBER, MULT, THAT, ALL -> false;
            default -> true;
        };
    }

    private static int distance(String a, String b, int cap) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > cap) return cap + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    public static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replace("'", "").replace("’", "").replace("ʼ", "").replace("`", "");
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            out.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return out.toString().strip();
    }

    public static Map<Kind, Integer> emptyCounts() {
        return new EnumMap<>(Kind.class);
    }
}
