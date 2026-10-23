package br.com.colheitafeliz.render;

import br.com.colheitafeliz.game.GameState;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

/** Technical tile outlines are intentionally absent from normal gameplay. */
public final class DebugOverlay {
    public void draw(ShapeRenderer shapes, SpriteBatch batch, GameAssets assets,
                     IsoRenderer iso, GameState state, double speed) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.RED);
        for (int row = 0; row < state.farm().rows(); row++)
            for (int col = 0; col < state.farm().columns(); col++) {
                Vector2 p = iso.gridToScreen(col, row);
                float w = IsoRenderer.TILE_WIDTH / 2f;
                float h = IsoRenderer.TILE_HEIGHT / 2f;
                shapes.line(p.x, p.y + h, p.x + w, p.y);
                shapes.line(p.x + w, p.y, p.x, p.y - h);
                shapes.line(p.x, p.y - h, p.x - w, p.y);
                shapes.line(p.x - w, p.y, p.x, p.y + h);
            }
        shapes.end();

        batch.begin();
        assets.font.setColor(Color.WHITE);
        assets.font.draw(batch, "DEBUG F3  |  grid 6x5  |  velocidade " + (int) speed + "x  |  tempo " + state.now(), 20, 145);
        batch.end();
    }
}
