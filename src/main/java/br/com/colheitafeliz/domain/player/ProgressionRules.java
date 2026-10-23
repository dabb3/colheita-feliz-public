package br.com.colheitafeliz.domain.player;

public record ProgressionRules(int levelBaseXp, int levelStepXp, int plantXp,
                               int waterXp, int removePestXp, int clearDeadXp) {
    public ProgressionRules {
        if (levelBaseXp <= 0 || levelStepXp < 0 || plantXp < 0 || waterXp < 0 || removePestXp < 0 || clearDeadXp < 0)
            throw new IllegalArgumentException("Invalid progression rules");
    }
}
