package br.com.colheitafeliz.desktop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

final class SaveRecoveryDialog {
    private SaveRecoveryDialog() {}

    static Path choose(Path failed, IOException error) {
        AtomicReference<Path> selected = new AtomicReference<>();
        Runnable dialog = () -> {
            int choice = JOptionPane.showOptionDialog(null,
                    "Não foi possível abrir a fazenda:\n" + failed + "\n\n" + error.getMessage()
                    + "\n\nO arquivo original não será substituído.\nVocê pode cancelar ou criar uma fazenda em outro arquivo."
                    + "\nPara reabrir a nova fazenda, use seu caminho em COLHEITA_SAVE_PATH.",
                    "Não foi possível abrir a fazenda", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                    null, new String[] {"Cancelar", "Nova fazenda em outro arquivo"}, "Cancelar");
            if (choice != 1) return;
            Path suggested = newFarmPath(failed);
            JFileChooser chooser = new JFileChooser(suggested.getParent().toFile());
            chooser.setDialogTitle("Escolha um arquivo novo para a fazenda");
            chooser.setSelectedFile(suggested.toFile());
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION)
                selected.set(chooser.getSelectedFile().toPath());
        };
        if (SwingUtilities.isEventDispatchThread()) dialog.run();
        else try { SwingUtilities.invokeAndWait(dialog); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (java.lang.reflect.InvocationTargetException e) { throw new IllegalStateException("Falha ao mostrar recuperação do save", e.getCause()); }
        return selected.get();
    }

    static Path newFarmPath(Path failed) {
        Path parent = failed.getParent();
        return (parent == null ? Path.of(System.getProperty("user.home")) : parent).resolve("nova-fazenda.json");
    }
}
