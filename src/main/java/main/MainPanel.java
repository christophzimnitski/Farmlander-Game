package main;

import inputs.KeyboardInputs;
import inputs.MouseInputs;

import javax.swing.*;
import java.awt.*;

import static main.Level.GAME_HEIGHT;
import static main.Level.GAME_WIDTH;

public class MainPanel extends JPanel {
    private final Level level;

    public MainPanel(Level level) {
        this.level = level;

        setPanelSize();

        addKeyListener(new KeyboardInputs(this));

        MouseInputs mouseInputs = new MouseInputs(this);
        addMouseListener(mouseInputs);
        addMouseMotionListener(mouseInputs);

    }

    @Override
    public Component add(Component comp) {
        return super.add(comp);
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
    }

    private void setPanelSize() {
        Dimension size = new Dimension(GAME_WIDTH, GAME_HEIGHT);
        setMinimumSize(size);
        setMaximumSize(size);
        setPreferredSize(size);
    }


    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        setBackground(new Color(48, 129, 54));
        level.render(g);
    }

    public Level getLevel() {
        return level;
    }

}
