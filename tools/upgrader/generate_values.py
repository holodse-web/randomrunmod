import json
import csv
import sys
import zipfile
from collections import Counter
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
jar = zipfile.ZipFile(sys.argv[1])
names = set(jar.namelist())
items = {n.removeprefix('assets/minecraft/items/').removesuffix('.json')
         for n in names if n.startswith('assets/minecraft/items/') and n.endswith('.json')}
items.add('air')

@lru_cache(None)
def tag(name):
    path = 'data/' + name.replace(':', '/tags/item/', 1) + '.json'
    if path not in names:
        return frozenset()
    result = set()
    for value in json.loads(jar.read(path))['values']:
        if isinstance(value, dict):
            value = value['id']
        result.update(tag(value[1:]) if value.startswith('#') else [value.split(':')[-1]])
    return frozenset(result)

def ingredient(value):
    if isinstance(value, str):
        return tag(value[1:]) if value.startswith('#') else frozenset([value.split(':')[-1]])
    if isinstance(value, list):
        return frozenset().union(*(ingredient(v) for v in value))
    return frozenset()

admin = set('air bedrock barrier light structure_void structure_block jigsaw command_block repeating_command_block chain_command_block command_block_minecart test_block test_instance_block debug_stick knowledge_book spawner trial_spawner vault end_portal_frame reinforced_deepslate budding_amethyst small_amethyst_bud medium_amethyst_bud large_amethyst_bud petrified_oak_slab suspicious_sand suspicious_gravel farmland dirt_path frogspawn player_head'.split())
metadata = set('potion splash_potion lingering_potion tipped_arrow enchanted_book written_book firework_star suspicious_stew'.split())
excluded = {}
for item in items:
    if item in admin or item.startswith('infested_') or item.endswith('_spawn_egg'):
        excluded[item] = 'Not obtainable as an ordinary vanilla Survival inventory item'
    elif item in metadata or item == 'filled_map' or item.endswith('_map'):
        excluded[item] = 'Requires variant/world metadata; a default stack is not a valid equivalent reward'

seeds = {}
source_groups = {}

def source(group, value, words):
    for item in words.split():
        if item not in items:
            raise AssertionError('Unknown source item: ' + item)
        assert item not in seeds, 'Duplicate source item: ' + item
        seeds[item] = value
        source_groups[item] = group

source('hand-dug-bulk-earth', 2, 'dirt coarse_dirt rooted_dirt grass_block podzol mycelium mud')
source('loose-construction-minerals', 4, 'sand red_sand gravel')
source('pickaxe-bulk-rock', 4, 'cobblestone stone granite diorite andesite tuff calcite basalt blackstone')
source('slower-deep-rock', 6, 'deepslate cobbled_deepslate')
source('frozen-renewable-water', 4, 'ice')
source('soft-bulk-nether-rock', 2, 'netherrack')
source('dimension-bulk-material', 12, 'end_stone soul_sand soul_soil sulfur cinnabar')
source('small-cave-growth', 6, 'pointed_dripstone sulfur_spike')
source('special-cave-material', 108, 'potent_sulfur')
source('clay-fragment', 3, 'clay_ball')
source('gravel-tool-material', 8, 'flint')
source('loose-snow', 1, 'snowball')
source('mined-fuel', 108, 'coal')
source('common-metal', 108, 'raw_copper copper_ingot')
source('iron-progression', 360, 'raw_iron iron_ingot')
source('less-abundant-metal', 720, 'raw_gold gold_ingot')
source('deep-mined-material', 5760, 'diamond')
source('renewable-villager-currency', 144, 'emerald')
source('common-ore-fragments', 36, 'redstone')
source('cave-nether-crystals', 72, 'lapis_lazuli quartz amethyst_shard')
source('netherite-mining', 11520, 'ancient_debris netherite_scrap')
source('slow-hard-block', 360, 'obsidian')
source('barter-and-ruined-portal-block', 540, 'crying_obsidian')

source('loose-foliage-and-grass', 1, 'dead_bush leaf_litter short_grass tall_grass fern large_fern short_dry_grass tall_dry_grass bush red_shrub seagrass nether_sprouts crimson_roots warped_roots hanging_roots pale_hanging_moss glow_lichen sculk_vein')
source('fallen-twigs-and-dead-bush-drops', 1, 'stick')
source('common-flowers', 2, 'dandelion poppy allium azure_bluet blue_orchid red_tulip orange_tulip white_tulip pink_tulip oxeye_daisy cornflower lily_of_the_valley sunflower lilac rose_bush peony pink_petals wildflowers cactus_flower open_eyeblossom closed_eyeblossom')
source('renewable-ground-growth', 2, 'azalea flowering_azalea firefly_bush moss_block pale_moss_block vine weeping_vines twisting_vines big_dripleaf small_dripleaf lily_pad sea_pickle mangrove_roots')
source('common-mushrooms', 4, 'brown_mushroom red_mushroom shelf_mushroom crimson_fungus warped_fungus brown_mushroom_block red_mushroom_block mushroom_stem')
source('nether-ground-cover', 12, 'crimson_nylium warped_nylium')
source('bulk-nether-growth', 4, 'nether_wart_block warped_wart_block')
source('nether-tree-light', 36, 'shroomlight')
source('rare-cave-decoration', 144, 'spore_blossom')
source('sniffer-crop', 144, 'torchflower_seeds pitcher_pod')
source('sniffer-flower', 288, 'torchflower pitcher_plant')
source('wither-gated-flower', 1440, 'wither_rose')
source('end-crop', 36, 'chorus_fruit chorus_plant')
source('end-crop-starter', 72, 'chorus_flower')
source('renewable-tall-crops', 6, 'sugar_cane')
source('fast-water-and-desert-crops', 2, 'kelp cactus')
source('bamboo-nine-stems-for-two-planks', 4, 'bamboo')
source('common-seeds', 1, 'wheat_seeds beetroot_seeds pumpkin_seeds melon_seeds')
source('harvested-grain', 24, 'wheat')
source('harvested-root-food', 16, 'carrot potato')
source('harvested-small-root-food', 12, 'beetroot')
source('inedible-farming-byproduct', 1, 'poisonous_potato')
source('common-fruit-portions', 4, 'melon_slice sweet_berries')
source('cave-fruit', 8, 'glow_berries cocoa_beans')
source('leaf-dropped-fruit', 24, 'apple')
source('whole-pumpkin', 24, 'pumpkin')
source('carved-pumpkin-after-four-seeds', 20, 'carved_pumpkin')
source('whole-melon-normal-harvest', 28, 'melon')
source('nether-fortress-crop', 48, 'nether_wart')

source('common-meat', 72, 'beef porkchop')
source('smaller-meat', 54, 'mutton rabbit')
source('small-meat-and-fish', 36, 'chicken cod')
source('larger-fish', 54, 'salmon')
source('decorative-fish', 72, 'tropical_fish')
source('brewing-fish', 108, 'pufferfish')
source('renewable-eggs', 12, 'egg blue_egg brown_egg')
source('animal-hide', 108, 'leather')
source('small-hide', 27, 'rabbit_hide')
source('common-mob-byproduct', 18, 'feather ink_sac')
source('basic-hostile-drop', 36, 'string bone')
source('hostile-junk', 4, 'rotten_flesh')
source('dangerous-hostile-drop', 108, 'gunpowder slime_ball')
source('spider-brewing-drop', 54, 'spider_eye')
source('nether-slime-drop', 162, 'magma_cream')
source('glowing-cave-drop', 72, 'glow_ink_sac')
source('bee-harvest', 36, 'honeycomb')
source('bottled-honey', 72, 'honey_bottle')
source('creaking-harvest', 36, 'resin_clump')
source('enderman-combat', 1440, 'ender_pearl')
source('fortress-and-trial-combat', 2160, 'blaze_rod breeze_rod')
source('ghast-combat', 2880, 'ghast_tear')
source('night-combat-drop', 1080, 'phantom_membrane')
source('end-city-combat', 5760, 'shulker_shell')
source('ocean-monument-combat', 108, 'prismarine_shard')
source('ocean-monument-crystals', 162, 'prismarine_crystals')
source('nether-light-mining', 108, 'glowstone_dust')
source('rare-animal-drop', 1440, 'rabbit_foot')
source('turtle-growth', 720, 'turtle_scute')
source('renewable-armadillo-brushing', 36, 'armadillo_scute')
source('turtle-breeding', 360, 'turtle_egg')
source('ocean-archeology-progression', 8640, 'sniffer_egg')
source('hive-without-occupants', 360, 'bee_nest')
source('string-equivalent-web', 36, 'cobweb')
source('copper-golem-construction', 996, 'copper_golem_statue')
source('sculk-bulk', 4, 'sculk')
source('sculk-redstone', 720, 'sculk_sensor')
source('sculk-progression', 1440, 'sculk_catalyst sculk_shrieker')
source('amethyst-cluster-drops', 288, 'amethyst_cluster')
source('gilded-nether-rock', 160, 'gilded_blackstone')
source('worn-anvil-two-stages-left', 7440, 'chipped_anvil')
source('worn-anvil-one-stage-left', 3720, 'damaged_anvil')
source('ocean-fishing-loot', 1440, 'nautilus_shell')
source('buried-treasure', 21600, 'heart_of_the_sea')
source('rare-fortress-drop', 21600, 'wither_skeleton_skull')
source('wither-boss', 86400, 'nether_star')
source('rare-ominous-vault-loot', 86400, 'heavy_core')
source('end-ship-progression', 172800, 'elytra')
source('dragon-trophy', 259200, 'dragon_egg')
source('end-ship-trophy', 28800, 'dragon_head')
source('raid-progression', 21600, 'totem_of_undying')
source('rare-drowned-drop', 28800, 'trident')
source('ancient-city-loot', 5760, 'echo_shard')
source('very-rare-structure-food', 57600, 'enchanted_golden_apple')
source('bottled-experience', 720, 'experience_bottle')
source('trial-chamber-reward', 2160, 'trial_key')
source('ominous-trial-reward', 5760, 'ominous_trial_key')
source('patrol-and-trial-bottle', 2880, 'ominous_bottle')
source('ancient-city-fragment', 2880, 'disc_fragment_5')
source('goat-horn-collection', 1440, 'goat_horn')
source('village-bell', 2880, 'bell')
source('ocean-monument-sponges', 2160, 'wet_sponge')
source('nether-barter-construction', 4320, 'dried_ghast')
source('dragon-fight-bottling', 2880, 'dragon_breath')
source('frog-nether-farm', 360, 'ochre_froglight verdant_froglight pearlescent_froglight')

for item in sorted(items - excluded.keys()):
    if item in seeds:
        continue
    group = None
    value = None
    if item.endswith('_leaves'):
        group, value = 'bulk-leaves', 1
    elif item.endswith(('_sapling', '_propagule')):
        group, value = 'renewable-tree-starter', 2
    elif item.endswith('_log') or item in ('crimson_stem', 'warped_stem', 'stripped_crimson_stem', 'stripped_warped_stem'):
        group, value = 'tree-trunk-four-planks', 72
    elif item.endswith('_wool'):
        group, value = 'renewable-sheep-wool', 36
    elif item.endswith('_smithing_template'):
        templates = {
            'netherite_upgrade': ('bastion-upgrade-template', 40320),
            'sentry_armor_trim': ('outpost-trim', 5760),
            'dune_armor_trim': ('desert-pyramid-trim', 5760),
            'coast_armor_trim': ('shipwreck-trim', 4320),
            'wild_armor_trim': ('jungle-temple-trim', 5760),
            'ward_armor_trim': ('ancient-city-trim', 11520),
            'eye_armor_trim': ('stronghold-trim', 8640),
            'vex_armor_trim': ('mansion-trim', 11520),
            'tide_armor_trim': ('elder-guardian-trim', 11520),
            'snout_armor_trim': ('bastion-trim', 8640),
            'rib_armor_trim': ('fortress-trim', 11520),
            'spire_armor_trim': ('end-city-trim', 14400),
            'wayfinder_armor_trim': ('trail-ruins-trim', 5760),
            'shaper_armor_trim': ('trail-ruins-trim', 5760),
            'raiser_armor_trim': ('trail-ruins-trim', 5760),
            'host_armor_trim': ('trail-ruins-trim', 5760),
            'silence_armor_trim': ('rare-ancient-city-trim', 40320),
            'flow_armor_trim': ('ominous-trial-vault-trim', 17280),
            'bolt_armor_trim': ('trial-vault-trim', 8640),
        }
        group, value = templates[item.removesuffix('_smithing_template')]
    elif item.startswith('music_disc_') and item != 'music_disc_5':
        rare = item in ('music_disc_pigstep', 'music_disc_otherside', 'music_disc_creator', 'music_disc_precipice', 'music_disc_relic', 'music_disc_tears')
        group, value = ('rare-structure-disc', 8640) if rare else ('mob-and-structure-disc', 4320)
    elif item.endswith('_pottery_sherd'):
        group, value = 'archeology-collection', 1440
    elif item.endswith('_ore'):
        ore = next((key for key in ('diamond', 'emerald', 'gold', 'iron', 'copper', 'coal', 'lapis', 'redstone', 'quartz') if key in item), None)
        assert ore, 'Unclassified ore ' + item
        group, value = 'ore-normal-drop-or-smelting-yield', {'diamond':5760, 'emerald':144, 'gold':720, 'iron':360, 'copper':540, 'coal':108, 'lapis':648, 'redstone':180, 'quartz':72}[ore]
        if item == 'nether_gold_ore':
            value = 720
    elif '_coral' in item:
        group, value = 'reef-decoration', 8 if item.endswith('_block') else 2
    elif item in ('skeleton_skull', 'zombie_head', 'creeper_head', 'piglin_head'):
        group, value = 'charged-creeper-trophy', 5760
    elif item.endswith(('_horse_armor', '_nautilus_armor')) and not item.startswith(('netherite_', 'leather_')):
        material = item.split('_')[0]
        group, value = 'loot-equipment-material', 7 * {'copper':108, 'iron':360, 'golden':720, 'diamond':5760}[material]
    elif item.startswith('chainmail_'):
        group, value = 'uncraftable-metal-equipment', {'chainmail_helmet':1800, 'chainmail_chestplate':2880, 'chainmail_leggings':2520, 'chainmail_boots':1440}[item]
    elif item in ('globe_banner_pattern', 'piglin_banner_pattern', 'flow_banner_pattern', 'guster_banner_pattern'):
        group, value = 'merchant-and-structure-pattern', 8640 if item.startswith('piglin') else 5760
    elif item.endswith('_bucket'):
        content = {'water_bucket':1, 'lava_bucket':360, 'milk_bucket':12, 'powder_snow_bucket':1, 'cod_bucket':36, 'salmon_bucket':54, 'tropical_fish_bucket':72, 'pufferfish_bucket':108, 'axolotl_bucket':720, 'tadpole_bucket':360, 'sulfur_cube_bucket':720}
        assert item in content, 'Unclassified bucket ' + item
        group, value = 'three-iron-bucket-plus-content', 1080 + content[item]
    if group:
        seeds[item] = value
        source_groups[item] = group

def oxidation_base(item):
    prefix = 'waxed_' if item.startswith('waxed_') else ''
    rest = item.removeprefix(prefix) if prefix else item
    for stage in ('exposed_', 'weathered_', 'oxidized_'):
        if rest.startswith(stage):
            base = rest.removeprefix(stage)
            if base == 'copper':
                base = 'copper_block'
            candidate = prefix + base
            return candidate if candidate in items else None
    return None

recipes = []
for path in sorted(names):
    if not path.startswith('data/minecraft/recipe/') or not path.endswith('.json'):
        continue
    data = json.loads(jar.read(path))
    kind = data['type'].split(':')[-1]
    result = data.get('result')
    if not isinstance(result, dict) or 'id' not in result or result.get('components'):
        continue
    target = result['id'].split(':')[-1]
    count = result.get('count', 1)
    if kind == 'crafting_shaped':
        ing = [ingredient(data['key'][c]) for row in data['pattern'] for c in row if c != ' ']
    elif kind == 'crafting_shapeless':
        ing = [ingredient(v) for v in data['ingredients']]
    elif kind in ('smelting', 'blasting', 'smoking', 'campfire_cooking', 'stonecutting'):
        ing = [ingredient(data['ingredient'])]
    elif kind == 'smithing_transform':
        ing = [ingredient(data[k]) for k in ('template', 'base', 'addition') if k in data]
    elif kind == 'crafting_transmute':
        ing = [ingredient(data['input']), ingredient(data['material'])]
    elif kind == 'crafting_special_firework_rocket':
        ing = [ingredient(data['fuel']), ingredient(data['shell'])]
    else:
        continue
    if target in items and target not in excluded and ing and all(ing):
        recipes.append((path.split('/')[-1], target, count, ing, kind))

ordinary_recipe_count = len(recipes)

for item in sorted(items):
    base = oxidation_base(item)
    if base:
        recipes.append(('oxidize:' + item, item, 1, [frozenset([base])], 'world_transform'))
        recipes.append(('scrape:' + item, base, 1, [frozenset([item])], 'world_transform'))
    if item.endswith('_concrete'):
        powder = item + '_powder'
        recipes.append(('hydrate:' + item, item, 1, [frozenset([powder])], 'world_transform'))
    if item.startswith('stripped_') and item.removeprefix('stripped_') in items:
        base = item.removeprefix('stripped_')
        recipes.append(('strip:' + item, item, 1, [frozenset([base])], 'world_transform'))
        recipes.append(('same-timber:' + item, base, 1, [frozenset([item])], 'world_transform'))

crafted = {r[1] for r in recipes}
unclassified = sorted(items - crafted - seeds.keys() - excluded.keys())
assert not unclassified, 'Natural items need an explicit acquisition group: ' + ', '.join(unclassified)
prices = {item: (0 if item in excluded else seeds.get(item, 10**12)) for item in items}
basis = {item: 'excluded' if item in excluded else 'source:' + source_groups[item] if item in source_groups else '' for item in items}
remainders = {'milk_bucket': 'bucket', 'water_bucket': 'bucket', 'lava_bucket': 'bucket', 'powder_snow_bucket': 'bucket', 'honey_bottle': 'glass_bottle', 'dragon_breath': 'glass_bottle'}

def cost(recipe):
    _, target, count, ingredients, kind = recipe
    total = 0
    for group in ingredients:
        options = []
        for item in group:
            price = prices.get(item, 0)
            if price <= 0:
                continue
            if kind in ('crafting_shaped', 'crafting_shapeless') and item in remainders:
                price -= prices[remainders[item]]
            options.append(max(0, price))
        if not options:
            return None
        total += min(options)
    return total // count

def normalize():
    for iteration in range(512):
        changed = False
        for recipe in recipes:
            limit = cost(recipe)
            item = recipe[1]
            if limit is not None and 0 < limit < prices[item]:
                prices[item] = limit
                basis[item] = 'recipe:' + recipe[0]
                changed = True
        if not changed:
            return iteration + 1
    raise RuntimeError('Price constraints did not converge')

normalize()
unresolved = sorted(item for item, price in prices.items() if price >= 10**12)
assert not unresolved, 'Unresolved recipe items need an explicit seed: ' + ', '.join(unresolved)
iterations = normalize()
violations = [r[0] for r in recipes if cost(r) is not None and prices[r[1]] > cost(r)]
assert not violations, violations
assert prices['iron_block'] == 9 * prices['iron_ingot'] == 81 * prices['iron_nugget']
assert prices['copper_block'] == 9 * prices['copper_ingot'] == 81 * prices['copper_nugget']
assert prices['gold_block'] == 9 * prices['gold_ingot'] == 81 * prices['gold_nugget']
assert prices['oak_log'] == 4 * prices['oak_planks']
assert prices['stick'] == 1 and 4 * prices['stick'] <= 2 * prices['oak_planks']
for item in items:
    base = oxidation_base(item)
    if base:
        assert prices[item] == prices[base], (item, base)
    if item.endswith('_concrete'):
        assert prices[item] <= prices[item + '_powder']
    if item.startswith('stripped_') and item.removeprefix('stripped_') in items:
        assert prices[item] == prices[item.removeprefix('stripped_')]
    if item.endswith('_leaves'):
        assert prices[item] == 1 < prices['dirt']
for item in 'dead_bush leaf_litter short_grass tall_grass fern large_fern short_dry_grass tall_dry_grass seagrass'.split():
    assert prices[item] == 1
for item in 'dandelion poppy moss_block moss_carpet kelp cactus'.split():
    assert 0 < prices[item] <= prices['dirt']
for item in 'sand red_sand gravel cobblestone stone'.split():
    assert prices['dirt'] <= prices[item] < prices['oak_planks']
assert prices['dirt'] < prices['stone'] < prices['oak_planks'] < prices['oak_log']
assert prices['oak_log'] == 72 and prices['oak_planks'] == 18 and prices['bamboo'] == 4
for wood in 'oak spruce birch jungle acacia dark_oak mangrove cherry pale_oak poplar crimson warped'.split():
    assert prices[wood + '_planks'] == 18, wood
    for part in 'stairs slab door trapdoor fence fence_gate pressure_plate button sign hanging_sign'.split():
        assert prices[wood + '_' + part] == prices['oak_' + part], (wood, part)
assert 9 * prices['bamboo'] == 2 * prices['bamboo_planks']
assert prices['bamboo_planks'] == prices['oak_planks']
assert prices['mushroom_stem'] < prices['oak_log']
assert prices['water_bucket'] == prices['bucket'] + 1
assert prices['damaged_anvil'] < prices['chipped_anvil'] < prices['anvil']
assert prices['copper_ingot'] < prices['iron_ingot'] < prices['diamond'] < prices['netherite_ingot']
assert prices['emerald'] < prices['diamond']
assert prices['iron_pickaxe'] == 3 * prices['iron_ingot'] + 2 * prices['stick']
assert prices['diamond_pickaxe'] == 3 * prices['diamond'] + 2 * prices['stick']
assert prices['ender_eye'] == prices['ender_pearl'] + prices['blaze_powder']
assert prices['mace'] == prices['heavy_core'] + prices['breeze_rod']
assert prices['bread'] == 3 * prices['wheat']
for raw, cooked in [('beef','cooked_beef'), ('porkchop','cooked_porkchop'), ('chicken','cooked_chicken'), ('cod','cooked_cod'), ('salmon','cooked_salmon'), ('potato','baked_potato')]:
    assert prices[cooked] <= prices[raw], (raw, cooked)
assert prices['name_tag'] == prices['paper'] + min(prices[n] for n in tag('minecraft:metal_nuggets'))
assert prices['silence_armor_trim_smithing_template'] > prices['coast_armor_trim_smithing_template']
assert prices['flow_armor_trim_smithing_template'] > prices['bolt_armor_trim_smithing_template']
assert prices['carved_pumpkin'] + 4 * prices['pumpkin_seeds'] <= prices['pumpkin']
assert all(basis[item] for item in items)
assert all(prices[item] > 0 for item in items - excluded.keys())

def normal_loot_maximum(block, item):
    data = json.loads(jar.read('data/minecraft/loot_table/blocks/' + block + '.json'))
    found = []
    def visit(node):
        if isinstance(node, list):
            for child in node:
                visit(child)
        elif isinstance(node, dict):
            if node.get('type') == 'minecraft:item' and node.get('name') == 'minecraft:' + item:
                count = 1
                modifiers = node.get('modifier', [])
                for modifier in modifiers if isinstance(modifiers, list) else [modifiers]:
                    if modifier.get('type') == 'minecraft:set_count':
                        bounds = modifier['count']
                        count = bounds.get('max', 1) if isinstance(bounds, dict) else bounds
                found.append(count)
            for child in node.values():
                if isinstance(child, (dict, list)):
                    visit(child)
    visit(data)
    assert found, 'Missing actual loot-table item ' + block + ':' + item
    return max(found)

loot_checks = [('copper_ore','raw_copper',5), ('redstone_ore','redstone',5), ('lapis_ore','lapis_lazuli',9), ('diamond_ore','diamond',1), ('coal_ore','coal',1), ('emerald_ore','emerald',1)]
for block, drop, maximum in loot_checks:
    assert normal_loot_maximum(block, drop) == maximum, (block, drop)
    assert prices[block] >= maximum * prices[drop]
assert prices['nether_gold_ore'] >= prices['gold_ingot']

groups = {item: 'excluded' if item in excluded else source_groups.get(item, 'recipe-material-cost') for item in items}
group_counts = Counter(groups.values())
dest = ROOT / 'src/main/resources/assets/randomrun/upgrader_values.json'
previous_catalog = json.loads(dest.read_text(encoding='utf-8')) if dest.exists() else {}
baseline = {key.split(':')[-1]: value for key, value in previous_catalog.get('values', {}).items()}
report_dir = ROOT / 'build/reports/upgrader'
report_dir.mkdir(parents=True, exist_ok=True)
audit_tsv = report_dir / 'CATALOG_AUDIT.tsv'
if previous_catalog.get('balance_revision') == 3 and audit_tsv.exists():
    with audit_tsv.open(encoding='utf-8', newline='') as file:
        baseline = {row['item'].split(':')[-1]: int(row['previous_points']) for row in csv.DictReader(file, delimiter='\t')}
changed = sum(prices[item] != baseline.get(item, prices[item]) for item in items)

catalog = {
    'minecraft_version': '26.3',
    'balance_revision': 3,
    'balance_note': 'Acquisition effort, abundance, progression and actual recipe costs. Dirt=2, stone=4, planks=18, logs=72, iron=360. All wood families and bamboo agree; loose twigs use their cheaper natural source. No generic fallback.',
    'coverage': {'vanilla_items': len(items), 'allowed_items': len(items)-len(excluded), 'excluded_items': len(excluded), 'audited_recipe_constraints': ordinary_recipe_count, 'world_transform_constraints': len(recipes) - ordinary_recipe_count, 'actual_ore_loot_checks': len(loot_checks), 'unclassified_items': len(unclassified), 'unresolved_recipe_items': len(unresolved), 'generic_fallback_items': 0},
    'values': {'minecraft:' + item: prices[item] for item in sorted(items)},
    'acquisition_groups': {'minecraft:' + item: groups[item] for item in sorted(items)},
    'price_basis': {'minecraft:' + item: basis[item] for item in sorted(items)},
    'excluded': {'minecraft:' + item: excluded[item] for item in sorted(excluded)},
}
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
with audit_tsv.open('w', encoding='utf-8', newline='') as file:
    writer = csv.writer(file, delimiter='\t', lineterminator='\n')
    writer.writerow(['item', 'acquisition_group', 'previous_points', 'current_points', 'price_basis', 'exclusion_reason'])
    writer.writerows(['minecraft:' + item, groups[item], baseline.get(item, prices[item]), prices[item], basis[item], excluded.get(item, '')] for item in sorted(items))
examples = 'dead_bush leaf_litter oak_leaves dirt stone sand oak_planks oak_log bamboo bamboo_planks stick coal charcoal white_wool wheat bread beef cooked_beef iron_ingot iron_pickaxe emerald diamond diamond_ore diamond_pickaxe name_tag netherite_ingot netherite_pickaxe ender_eye heavy_core mace elytra'.split()
report = [
    '# Acquisition balance audit: revision 3', '',
    f'Every one of the {len(items):,} item IDs is assigned a named acquisition group or an actual material recipe, with a recorded price basis; {changed:,} values changed from revision 2. {len(items)-len(excluded):,} allowed and {len(excluded)} explicitly excluded. No generic fallback, unclassified natural source or unresolved recipe item remains.', '',
    f'Checks passed: {ordinary_recipe_count:,} actual recipe inequalities and {len(recipes)-ordinary_recipe_count} world conversion constraints (oxidation/scraping including lightning rods, concrete hydration, stripped timber/bamboo). All metal compression and wood conversion assertions passed.', '',
    'The previous basis priced dirt above planks. It was wrong for this mode: plentiful hand-dug ground must not command a timber premium. The new acquisition ladder is dirt 2 < mined stone 4 < planks 18 < logs 72. All twelve regular/nether wood families have identical usable timber values, with no arbitrary color/biome markup. Nine bamboo at 4 form a 36-point block that makes two 18-point planks. Recipe variants (stairs, doors, fences, signs and hanging signs) agree across these families.', '',
    'Loose leaves, dry bushes, grass and leaf litter stay 1. Common flowers and moss are 2. Sticks are also 1 because dead bushes and leaves provide them without spending planks; a recipe is a maximum material cost, not a guarantee that an easier natural source is unavailable. Wool likewise uses renewable sheep shearing rather than four expensive spider strings. This deliberately gives no profit for crafting cheap natural byproducts out of expensive materials.', '',
    'Stone and ground use bulk extraction costs; metal/mineral prices reflect needing ore, the proper tool, mining and progression. Copper 108 < iron 360 < gold 720 < diamond 5760 < netherite ingot 48960. Emerald 144 reflects renewable villager trades. The actual 26.3 block loot tables were checked: normal copper ore drops up to 5 raw copper, redstone up to 5 dust and lapis up to 9 pieces. Ore prices cover these normal outputs; diamond/iron/gold/coal ore carry their resource value. Nether gold ore covers its full smelted ingot, avoiding a cheap-ore route into gold.', '',
    'Crops are cheaper than ordinary hunted meat, and ordinary meat cheaper than dangerous progression drops: wheat 24, bread 72, beef 72, ender pearl 1440, blaze rod 2160. Cooking does not add points just for clicking a furnace or using a free campfire. Recipes with returned buckets/bottles subtract those containers, so cake does not become valuable iron. Buckets preserve their three ingots and water adds only 1 point.', '',
    'Pristine tools/armor use their actual cheapest materials; durability reduces the offered stack value. Consequently a stone tool can cost less than a wooden one when its stone ingredients are cheaper. Functional tool strength alone is not a free sale premium. Netherite includes the diamond equipment, ingot and consumed upgrade template. Structure drops, trophies, mob equipment, archaeology and boss progression have named source groups. Trim templates now differ by their actual acquisition routes (e.g. coast 4320, silence 40320, bolt 8640, ominous flow 17280), and duplication recipes still cap their price.', '',
    'The 26.3 name-tag recipe was verified directly: paper 6 + the cheapest metal nugget (copper 12) = 18, superseding its old loot-only premium. Other outlier checks cover mushroom stems versus timber, anvil wear, dragon breath, all ore drop quantities, metal blocks/nuggets, bamboo/stripping, and cheap consolation items.', '',
    'These are internally consistent Survival balance points, not official Minecraft prices or a promise to model every farm and player economy. The full per-item before/after audit is in [CATALOG_AUDIT.tsv](CATALOG_AUDIT.tsv).', '',
    '| Item | Revision 2 | Revision 3 | Source group |', '| --- | ---: | ---: | --- |',
]
report.extend(f'| {item} | {baseline.get(item, prices[item])} | {prices[item]} | {groups[item]} |' for item in examples)
report.extend(['', '## Complete classification coverage', '', '| Acquisition group | Items |', '| --- | ---: |'])
report.extend(f'| {group} | {count} |' for group, count in sorted(group_counts.items()))
(report_dir / 'BALANCE_AUDIT.md').write_text('\n'.join(report) + '\n', encoding='utf-8')
print(json.dumps(catalog['coverage']))
print('Changed values from revision 2:', changed, '; acquisition groups:', len(group_counts))
print('Recipe inequalities: all passed; final normalization iterations:', iterations)
print('Sample values:', {k: prices[k] for k in ['dirt','oak_log','oak_planks','stick','iron_ingot','iron_pickaxe','diamond','diamond_pickaxe','ender_eye','netherite_ingot','netherite_pickaxe','elytra','mace','sulfur','cinnabar','poplar_log','copper_spear','netherite_spear']})
