package br.com.minimercadinho.saas.application.service.delivery;

final class DeliveryRouteOptimizer {

    private DeliveryRouteOptimizer() {
    }

    static RouteOrder optimize(final double[][] costs) {
        final int stopCount = costs.length - 1;
        if (stopCount <= 0) {
            return new RouteOrder(new int[0], 0.0d);
        }
        if (stopCount == 1) {
            return new RouteOrder(new int[]{0}, costs[0][1]);
        }

        final int stateCount = 1 << stopCount;
        final double[][] dp = new double[stateCount][stopCount];
        final int[][] parent = new int[stateCount][stopCount];
        final double infinity = Double.POSITIVE_INFINITY;
        for (int state = 0; state < stateCount; state++) {
            for (int last = 0; last < stopCount; last++) {
                dp[state][last] = infinity;
                parent[state][last] = -1;
            }
        }

        for (int last = 0; last < stopCount; last++) {
            final int state = 1 << last;
            dp[state][last] = costs[0][last + 1];
        }

        for (int state = 1; state < stateCount; state++) {
            for (int last = 0; last < stopCount; last++) {
                if ((state & (1 << last)) == 0) {
                    continue;
                }
                final int previousState = state ^ (1 << last);
                if (previousState == 0) {
                    continue;
                }
                for (int previous = 0; previous < stopCount; previous++) {
                    if ((previousState & (1 << previous)) == 0) {
                        continue;
                    }
                    final double candidate = dp[previousState][previous]
                            + costs[previous + 1][last + 1];
                    if (candidate < dp[state][last]) {
                        dp[state][last] = candidate;
                        parent[state][last] = previous;
                    }
                }
            }
        }

        final int fullState = stateCount - 1;
        int lastStop = 0;
        double bestCost = infinity;
        for (int last = 0; last < stopCount; last++) {
            if (dp[fullState][last] < bestCost) {
                bestCost = dp[fullState][last];
                lastStop = last;
            }
        }

        final int[] order = new int[stopCount];
        int state = fullState;
        int position = stopCount - 1;
        int current = lastStop;
        while (position >= 0) {
            order[position] = current;
            final int previous = parent[state][current];
            state ^= 1 << current;
            current = previous;
            position--;
        }
        return new RouteOrder(order, bestCost);
    }

    record RouteOrder(
            int[] stopIndexes,
            double totalCost
    ) {
    }
}
