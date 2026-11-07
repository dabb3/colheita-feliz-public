package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.game.ActionResult;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.GameText;
import br.com.colheitafeliz.render.Palette;
import br.com.colheitafeliz.render.UiSkin;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;

/** A pending intention only; GameState validates the purchase when confirmed. */
public final class PlotPurchasePanel {
    private boolean open;
    private int column,row;
    public void open(int column,int row){this.column=column;this.row=row;open=true;}
    public boolean isOpen(){return open;}
    public void close(){open=false;}
    public static Rectangle bounds(){return new Rectangle(500,290,600,320);}
    public static Rectangle confirmButton(){return new Rectangle(820,318,240,44);}
    public static Rectangle cancelButton(){return new Rectangle(540,318,240,44);}
    public static Rectangle closeButton(){return new Rectangle(1050,568,36,32);}
    public ActionResult click(float x,float y,GameState state){
        if(!open)return null;
        if(closeButton().contains(x,y)||cancelButton().contains(x,y)){close();return null;}
        if(!confirmButton().contains(x,y))return null;
        close();
        return state.buyPlot(column,row);
    }
    public void draw(SpriteBatch batch,UiSkin skin,GameState state){
        if(!open)return;
        var b=bounds();var plot=state.land().at(column,row);
        skin.dropShadow(batch,b.x,b.y,b.width,b.height,4);skin.draw(batch,skin.window,b.x,b.y,b.width,b.height);
        skin.draw(batch,skin.windowBar,b.x+1,b.y+b.height-48,b.width-2,47);
        skin.text(batch,skin.title,"Comprar terreno?",b.x+24,b.y+b.height-15,Palette.INK,Align.left);
        skin.text(batch,skin.label,"X",closeButton().x+18,closeButton().y+24,Palette.INK,Align.center);
        skin.text(batch,skin.label,"Terreno ("+(column+1)+", "+(row+1)+") · nível "+plot.requiredLevel(),b.x+32,b.y+235,Palette.INK,Align.left);
        String currency=plot.currency()==br.com.colheitafeliz.domain.economy.Currency.GOLD?" ouro":" verdes";
        skin.text(batch,skin.body,"Preço: "+GameText.money(plot.price())+currency,b.x+32,b.y+190,Palette.INK,Align.left);
        skin.text(batch,skin.body,"Saldo disponível: "+GameText.money(state.wallet().balance(plot.currency()))+currency,b.x+32,b.y+155,Palette.INK,Align.left);
        skin.text(batch,skin.caption,"A compra segue a ordem de liberação dos terrenos.",b.x+32,b.y+110,Palette.INK_MUTED,Align.left);
        for(var button:new Rectangle[]{cancelButton(),confirmButton()})skin.draw(batch,skin.glassSun,button.x,button.y,button.width,button.height);
        skin.text(batch,skin.label,"Cancelar",660,348,Palette.INK,Align.center);
        skin.text(batch,skin.label,"Confirmar compra",940,348,Palette.INK,Align.center);
    }
}
