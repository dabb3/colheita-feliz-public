package br.com.colheitafeliz.render;

/** Posições lógicas da referência visual; independem da ordem de compra ou do catálogo. */
record AnimalLayout(float x, float y, float width, boolean insidePen) {
    static AnimalLayout forType(String id) {
        return switch (id) {
            case "galinha" -> new AnimalLayout(1170, 425, 62, true);
            case "pato" -> new AnimalLayout(1350, 350, 68, true);
            case "porco" -> new AnimalLayout(1445, 345, 80, true);
            case "ovelha" -> new AnimalLayout(1375, 435, 84, true);
            case "vaca" -> new AnimalLayout(1515, 455, 110, true);
            case "abelha" -> new AnimalLayout(1210, 60, 156, false);
            case "pombo" -> new AnimalLayout(1330, 230, 60, false);
            case "coelho" -> new AnimalLayout(195, 100, 72, false);
            case "burro" -> new AnimalLayout(275, 480, 96, false);
            case "pavao" -> new AnimalLayout(1020, 620, 80, false);
            case "avestruz" -> new AnimalLayout(1220, 635, 90, false);
            case "cabra" -> new AnimalLayout(220, 555, 78, false);
            case "cavalo" -> new AnimalLayout(395, 45, 100, false);
            case "cachorro-robo" -> new AnimalLayout(745, 480, 76, false);
            case "cachorro-mastiff" -> new AnimalLayout(1430, 60, 94, false);
            default -> throw new IllegalArgumentException("No scenery placement for " + id);
        };
    }
}
