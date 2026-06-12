package dev.chronica;

import java.util.List;
import java.util.Random;

public final class NameGenerator {
    private final long worldSeed;
    private final List<PhonemeSet> sets;

    private NameGenerator(long worldSeed) {
        this.worldSeed = worldSeed;
        this.sets = List.of(
                new PhonemeSet("KETH", List.of("ka", "ke", "kor", "thal", "dun", "ar", "vek"), List.of("Arun", "Vek", "Doram", "Khal", "Tharos")),
                new PhonemeSet("VEL", List.of("vel", "va", "sei", "lun", "mir", "or", "ae"), List.of("Domar", "Sae", "Luneth", "Vey", "Aurel")),
                new PhonemeSet("NAR", List.of("nar", "um", "gar", "bel", "tor", "ish", "mak"), List.of("Gorun", "Bel", "Makhar", "Torum", "Ishkar"))
        );
    }

    public static NameGenerator create(long worldSeed) {
        return new NameGenerator(worldSeed);
    }

    public String generateCivName(long seed) {
        Random random = random(seed, 11);
        PhonemeSet set = pick(random);
        return capitalize(syllables(random, set, 1 + random.nextInt(2))) + " " + set.suffixes.get(random.nextInt(set.suffixes.size()));
    }

    public String generateSettlementName(CivId civId, long seed) {
        Random random = random(seed ^ civId.value().getMostSignificantBits(), 23);
        PhonemeSet set = pickFor(civId);
        String base = capitalize(syllables(random, set, 2 + random.nextInt(2)));
        return switch (random.nextInt(5)) {
            case 0 -> base + " Hold";
            case 1 -> base + " Crossing";
            case 2 -> base + " Gate";
            case 3 -> base + " Vale";
            default -> base;
        };
    }

    public String generateNPCName(CivId civId, NPCRole role, long seed) {
        Random random = random(seed ^ civId.value().getLeastSignificantBits() ^ role.ordinal(), 37);
        PhonemeSet set = pickFor(civId);
        String given = capitalize(syllables(random, set, 2 + random.nextInt(2)));
        String epithet = switch (role) {
            case ELDER -> "the Elder";
            case MERCHANT -> "of the Scales";
            case GUARD_CAPTAIN -> "Shield-Bearer";
            case SCOUT -> "Farwalker";
            case DIPLOMAT -> "Soft-Voice";
            case GENERAL -> "Ironhand";
            case CITIZEN -> "of " + set.name;
        };
        return given + " " + epithet;
    }

    ProceduralHistoryWriter.Style styleFor(CultureTrait trait) {
        return switch (trait) {
            case SCHOLARLY, MERCANTILE -> ProceduralHistoryWriter.Style.SCHOLARLY;
            case AGGRESSIVE, MILITARIST, EXPANSIONIST -> ProceduralHistoryWriter.Style.EPIC;
            case ISOLATIONIST, NOMADIC, SPIRITUAL -> ProceduralHistoryWriter.Style.TRAGIC;
        };
    }

    private PhonemeSet pickFor(CivId civId) {
        int index = Math.floorMod(civId.value().hashCode(), sets.size());
        return sets.get(index);
    }

    private PhonemeSet pick(Random random) {
        return sets.get(random.nextInt(sets.size()));
    }

    private Random random(long seed, int salt) {
        return new Random(worldSeed ^ seed ^ (long) salt * 0x9E3779B97F4A7C15L);
    }

    private static String syllables(Random random, PhonemeSet set, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(set.syllables.get(random.nextInt(set.syllables.size())));
        return out.toString();
    }

    private static String capitalize(String text) {
        if (text.isBlank()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private record PhonemeSet(String name, List<String> syllables, List<String> suffixes) {}
}
