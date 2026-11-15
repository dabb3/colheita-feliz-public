package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class LoginRewardCalendar {
    record Reward(int day, String kind, String currency, String amount, String itemId) {}
    record NextDay(int day, boolean available, boolean streakReset) {}
    private static final List<Reward> REWARDS = createRewards();

    private LoginRewardCalendar() {}

    static List<Reward> rewards() { return REWARDS; }

    static Reward reward(int day) { return REWARDS.get(day - 1); }

    static void requireCrops(CropCatalog catalog) {
        for (var reward : REWARDS) if (reward.kind().equals("SEED")) catalog.get(reward.itemId());
    }

    static NextDay nextDay(LocalDate lastClaimDate, int completedDay, LocalDate today) {
        if (lastClaimDate == null) return new NextDay(1, true, false);
        if (!today.isAfter(lastClaimDate)) return new NextDay(completedDay == 30 ? 1 : completedDay + 1, false, false);
        if (!today.equals(lastClaimDate.plusDays(1))) return new NextDay(1, true, true);
        return new NextDay(completedDay == 30 ? 1 : completedDay + 1, true, false);
    }

    private static List<Reward> createRewards() {
        int[] gold = {250, 500, 750, 1000, 1500, 2000, 250, 500, 750, 1000, 1500, 2000,
                250, 500, 750, 1000, 1500, 2000, 250, 500, 750, 1000, 1500, 2000, 2500, 3000};
        String[] seedIds = {"semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"};
        var rewards = new ArrayList<Reward>(30);
        int goldIndex = 0, seedIndex = 0;
        for (int day = 1; day <= 30; day++) {
            if (day == 7 || day == 14 || day == 21 || day == 30) {
                rewards.add(new Reward(day, "SEED", "GREEN", "1", seedIds[seedIndex++]));
            } else {
                int amount = gold[goldIndex++];
                rewards.add(new Reward(day, "GOLD", "GOLD", Integer.toString(amount), null));
            }
        }
        return List.copyOf(rewards);
    }
}
