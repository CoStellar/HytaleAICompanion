package hytale.ai.rl;

/**
 * Zdyskretyzowany stan srodowiska uzywanym przez agenta Q-Learning.
 *
 * playerHpBucket: 0=krytyczne(<25%), 1=niskie(25-50%), 2=srednie(50-75%), 3=pelne(>75%)
 * enemyCountBucket: 0=brak, 1=jeden, 2=dwoch-trzech, 3=czterech+
 * distanceBucket: 0=blisko(<6 blokow), 1=srednia(6-15), 2=daleko(>15)
 *
 * Laczna liczba stanow: 4 x 4 x 3 = 48
 */
public record RLState(int playerHpBucket, int enemyCountBucket, int distanceBucket) {

    public int toIndex() {
        return playerHpBucket * 12 + enemyCountBucket * 3 + distanceBucket;
    }

    public static int totalStates() { return 48; }

    public boolean hasCombatThreat() { return enemyCountBucket > 0; }
}
