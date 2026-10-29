package br.com.colheitafeliz.render;

/** Posições lógicas da referência visual; independem da ordem de compra ou do catálogo. */
record AnimalLayout(float x, float y, float width, boolean insidePen) {
    static AnimalLayout forType(String id) {
        return switch (id) {
            case "galinha" -> new AnimalLayout(1170, 425, 62, true);
            case "pato" -> new AnimalLayout(1350, 350, 48, true);
            case "porco" -> new AnimalLayout(1480, 325, 125, true);
            case "ovelha" -> new AnimalLayout(1375, 435, 84, true);
            case "vaca" -> new AnimalLayout(1515, 455, 145, true);
            case "abelha" -> new AnimalLayout(1210, 60, 156, false);
            case "pombo" -> new AnimalLayout(1330, 230, 110, false);
            case "coelho" -> new AnimalLayout(195, 100, 72, false);
            case "burro" -> new AnimalLayout(300, 470, 140, false);
            case "pavao" -> new AnimalLayout(1020, 620, 80, false);
            case "avestruz" -> new AnimalLayout(1220, 635, 90, false);
            case "cabra" -> new AnimalLayout(180, 570, 110, false);
            case "cavalo" -> new AnimalLayout(395, 45, 150, false);
            case "cachorro-robo" -> new AnimalLayout(785, 490, 110, false);
            case "cachorro-mastiff" -> new AnimalLayout(1430, 60, 135, false);
            default -> throw new IllegalArgumentException("No scenery placement for " + id);
        };
    }
}
