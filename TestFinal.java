package prj;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestFinal extends JPanel implements ActionListener {

    private static class Bar {
        int value;
        double x;
        boolean sorted;
        Color currentColor;
        Bar(int value, double x, Color initColor) { this.value = value; this.x = x; this.sorted = false; this.currentColor = initColor; }
    }

    private Bar[] bars;
    private int barCount = 60;

    private static final int BASE_SWAP_DURATION_MS = 200;
    private static final int TARGET_FRAME_MS = 12; // ~83 FPS target (was 15)
    private static final int FRAME_DELAY_MS = TARGET_FRAME_MS;

    private volatile boolean sorting = false;
    private volatile boolean paused = false;
    private volatile boolean singleStep = false;
    private String algorithm = "Bubble Sort";

    private int activeIndex = -1;
    private int comparedIndex = -1;
    private int specialIndex = -1;

    private int flashIndex = -1;
    private float flashProgress = 0f;
    private volatile boolean flashing = false;

    private final JButton startBtn = new JButton("Start");
    private final JButton pauseBtn = new JButton("Pause");
    private final JButton stepBtn = new JButton("Step");
    private final JButton randomBtn = new JButton("Randomize");
    // Heap Sort removed from the list
    private final JComboBox<String> algoBox = new JComboBox<>(new String[]{
            "Bubble Sort","Selection Sort","Insertion Sort","Merge Sort","Quick Sort"
    });
    private final JSlider speedSlider = new JSlider(1, 60, 10);
    private final JButton slowBtn = new JButton("Slow");
    private final JButton normalBtn = new JButton("Normal");
    private final JButton fastBtn = new JButton("Fast");
    private final JButton ultraBtn = new JButton("Ultra");
    private final JSlider countSlider = new JSlider(10, 200, barCount);
    private final JLabel speedLbl = new JLabel("Speed: 1.0x");
    private final JLabel timeLbl = new JLabel("Last visual run: - ms");
    private final JToggleButton soundToggle = new JToggleButton("Sound: ON", true);

    private static final Color BG_COLOR = new Color(18, 18, 18);
    private static final Color BASE_COLOR = new Color(70, 130, 180);
    private static final Color ACTIVE_COLOR = new Color(80, 200, 120);
    private static final Color COMPARE_COLOR = new Color(255, 80, 80);
    private static final Color SORTED_COLOR = new Color(100, 220, 100);
    private static final Color FLASH_COLOR = new Color(255, 220, 60);
    private static final Color SPECIAL_COLOR = new Color(180, 100, 255);
    private static final Color PANEL_BG = new Color(28, 28, 28, 220);

    private final Object pauseLock = new Object();

    private long lastRunTimeMs = -1;

    private final ExecutorService soundExec = Executors.newSingleThreadExecutor();

    public TestFinal() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(1100, 600));
        setLayout(null);

        JPanel controls = new JPanel(null);
        controls.setBounds(8, 6, 1084, 72);
        controls.setBackground(PANEL_BG);
        controls.setBorder(BorderFactory.createLineBorder(new Color(60,60,60)));
        add(controls);

        startBtn.setBounds(10, 10, 90, 26);
        pauseBtn.setBounds(110, 10, 90, 26);
        stepBtn.setBounds(210, 10, 70, 26);
        randomBtn.setBounds(290, 10, 110, 26);
        algoBox.setBounds(410, 10, 160, 26);
        speedLbl.setBounds(10, 40, 140, 20);
        speedSlider.setBounds(70, 40, 240, 20);
        slowBtn.setBounds(320, 40, 60, 22);
        normalBtn.setBounds(390, 40, 70, 22);
        fastBtn.setBounds(465, 40, 60, 22);
        ultraBtn.setBounds(530, 40, 70, 22);
        JLabel countLabel = new JLabel("Bars:");
        countLabel.setForeground(Color.LIGHT_GRAY);
        countLabel.setBounds(610, 40, 40, 20);
        countSlider.setBounds(650, 40, 230, 20);
        timeLbl.setBounds(890, 10, 200, 20);
        timeLbl.setForeground(Color.LIGHT_GRAY);
        soundToggle.setBounds(890, 36, 120, 28);

        startBtn.setBackground(new Color(60,60,60));
        startBtn.setForeground(Color.WHITE);
        pauseBtn.setBackground(new Color(60,60,60));
        pauseBtn.setForeground(Color.WHITE);
        stepBtn.setBackground(new Color(60,60,60));
        stepBtn.setForeground(Color.WHITE);
        randomBtn.setBackground(new Color(60,60,60));
        randomBtn.setForeground(Color.WHITE);
        algoBox.setBackground(new Color(48,48,48));
        algoBox.setForeground(Color.WHITE);
        speedSlider.setBackground(PANEL_BG);
        countSlider.setBackground(PANEL_BG);
        slowBtn.setBackground(new Color(48,48,48));
        slowBtn.setForeground(Color.WHITE);
        normalBtn.setBackground(new Color(48,48,48));
        normalBtn.setForeground(Color.WHITE);
        fastBtn.setBackground(new Color(48,48,48));
        fastBtn.setForeground(Color.WHITE);
        ultraBtn.setBackground(new Color(48,48,48));
        ultraBtn.setForeground(Color.WHITE);
        soundToggle.setBackground(new Color(48,48,48));
        soundToggle.setForeground(Color.WHITE);
        speedLbl.setForeground(Color.LIGHT_GRAY);

        controls.add(startBtn);
        controls.add(pauseBtn);
        controls.add(stepBtn);
        controls.add(randomBtn);
        controls.add(algoBox);
        controls.add(speedLbl);
        controls.add(speedSlider);
        controls.add(slowBtn);
        controls.add(normalBtn);
        controls.add(fastBtn);
        controls.add(ultraBtn);
        controls.add(countLabel);
        controls.add(countSlider);
        controls.add(timeLbl);
        controls.add(soundToggle);

        startBtn.addActionListener(this);
        pauseBtn.addActionListener(this);
        stepBtn.addActionListener(this);
        randomBtn.addActionListener(this);
        algoBox.addActionListener(this);

        slowBtn.addActionListener(e -> setSpeedPreset(0.3));
        normalBtn.addActionListener(e -> setSpeedPreset(1.0));
        fastBtn.addActionListener(e -> setSpeedPreset(3.0));
        ultraBtn.addActionListener(e -> setSpeedPreset(6.0));

        speedSlider.addChangeListener(this::updateSpeedLabel);
        countSlider.addChangeListener((ChangeEvent e) -> {
            if (!sorting) {
                barCount = countSlider.getValue();
                randomizeBars();
            }
        });

        soundToggle.addActionListener(e -> {
            boolean on = soundToggle.isSelected();
            soundToggle.setText(on ? "Sound: ON" : "Sound: OFF");
        });

        randomizeBars();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resetBarXsToIndices();
                repaint();
            }
        });
    }

    private void setSpeedPreset(double mult) {
        int val = (int) Math.round(mult * 10.0);
        val = Math.max(1, Math.min(60, val));
        speedSlider.setValue(val);
        updateSpeedLabel(null);
    }

    private double getSpeedMultiplier() {
        return Math.max(0.1, speedSlider.getValue() / 10.0);
    }

    private void updateSpeedLabel(ChangeEvent ignored) {
        speedLbl.setText(String.format("Speed: %.1fx", getSpeedMultiplier()));
    }

    private void randomizeBars() {
        if (sorting) return;
        Random r = new Random();
        bars = new Bar[barCount];
        int panelWidth = Math.max(getWidth(), 800);
        double w = (double) panelWidth / barCount;
        Color init = BASE_COLOR;
        for (int i = 0; i < barCount; i++) {
            int val = r.nextInt(Math.max(20, getHeight() - 160)) + 20;
            bars[i] = new Bar(val, i * w, init);
        }
        activeIndex = comparedIndex = specialIndex = -1;
        flashIndex = -1;
        flashProgress = 0f;
        lastRunTimeMs = -1;
        timeLbl.setText("Last visual run: - ms");
        repaint();
    }

    // compute the screen X for a logical index (considers padding)
    private double getBarTargetX(int logicalIndex) {
        int paddingLeft = 40;
        int paddingRight = 40;
        int availableWidth = Math.max(2, getWidth() - paddingLeft - paddingRight);
        double slot = (double) availableWidth / Math.max(1, barCount);
        return paddingLeft + logicalIndex * slot;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g2 = (Graphics2D) g0;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2.setColor(BG_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int paddingLeft = 40;
        int paddingRight = 40;
        int paddingBottom = 60;
        int availableWidth = Math.max(2, getWidth() - paddingLeft - paddingRight);
        int barWidth = Math.max(4, availableWidth / Math.max(1, barCount));
        int xOffset = paddingLeft;
        int maxBarHeight = Math.max(20, getHeight() - paddingBottom - 100);

        for (int i = 0; i < barCount; i++) {
            Bar b = bars[i];

            Color target;
            if (b.sorted) target = SORTED_COLOR;
            else if (i == activeIndex) target = ACTIVE_COLOR;
            else if (i == comparedIndex) target = COMPARE_COLOR;
            else if (i == specialIndex) target = SPECIAL_COLOR;
            else target = BASE_COLOR;

            b.currentColor = lerpColor(b.currentColor, target, 0.18f);

            int h = (int) Math.round(clamp(b.value, 6, maxBarHeight));
            // Use animated b.x now (was drawing at i*barWidth which prevented visible motion)
            int x = xOffset + (int) Math.round(b.x - xOffset); // b.x is absolute already from getBarTargetX
            // simpler: place using b.x directly:
            x = (int) Math.round(b.x);

            int y = getHeight() - paddingBottom - h;

            int round = 12;
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(x + 3, y + 4, barWidth - 6, h, round, round);

            GradientPaint gp = new GradientPaint(
                    x, y,
                    b.currentColor.brighter(),
                    x, y + h,
                    b.currentColor.darker()
            );
            g2.setPaint(gp);

            g2.fillRoundRect(x, y, barWidth - 6, h, round, round);

            if (i == activeIndex || i == comparedIndex) {
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.14f));
                g2.setColor(b.currentColor);
                g2.fillRoundRect(x - 4, y - 6, barWidth + 2, h + 12, round + 6, round + 6);
                g2.setComposite(old);
            }

            if (i == flashIndex && flashing) {
                int alpha = (int) (255 * clamp(flashProgress, 0f, 1f));
                Color overlay = new Color(FLASH_COLOR.getRed(), FLASH_COLOR.getGreen(), FLASH_COLOR.getBlue(), alpha);
                g2.setColor(overlay);
                g2.fillRoundRect(x, y, barWidth - 6, h, round, round);
            }

            g2.setColor(new Color(0,0,0,80));
            g2.drawRoundRect(x, y, barWidth - 6, h, round, round);
        }

        g2.setColor(new Color(200,200,200,200));
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
        g2.drawString("Algorithm: " + algorithm, 14, getHeight() - 28);
        g2.drawString(String.format("Speed: %.1fx", getSpeedMultiplier()), 250, getHeight() - 28);
        g2.setColor(new Color(180,180,180,180));
        g2.drawString("Green = sorted    Mint = moving    Red = compared", 420, getHeight() - 28);
    }

    private double clamp(double v, double a, double b) {
        return v < a ? a : (v > b ? b : v);
    }

    private Color lerpColor(Color a, Color b, float t) {
        if (a == null) return b;
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(clampInt(r,0,255), clampInt(g,0,255), clampInt(bl,0,255), clampInt(al,0,255));
    }

    private int clampInt(int v, int a, int b) {
        return v < a ? a : (v > b ? b : v);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == startBtn) {
            if (!sorting) {
                algorithm = (String) algoBox.getSelectedItem();
                startSortingThread();
            }
        } else if (src == pauseBtn) {
            if (!sorting) return;
            synchronized (pauseLock) {
                paused = !paused;
                if (!paused) {
                    pauseLock.notifyAll();
                    pauseBtn.setText("Pause");
                } else pauseBtn.setText("Resume");
            }
        } else if (src == stepBtn) {
            if (!sorting) {
                algorithm = (String) algoBox.getSelectedItem();
                startSortingThread();
            } else {
                synchronized (pauseLock) {
                    singleStep = true;
                    pauseLock.notifyAll();
                }
            }
        } else if (src == randomBtn) {
            randomizeBars();
        } else if (src == algoBox) {
            algorithm = (String) algoBox.getSelectedItem();
        }
    }

    private void startSortingThread() {
        sorting = true;
        paused = false;
        singleStep = false;
        pauseBtn.setText("Pause");
        resetBarXsToIndices();

        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                runSortVisual();
            } catch (InterruptedException ignored) {
            } finally {
                sorting = false;
                activeIndex = comparedIndex = specialIndex = -1;
                for (Bar b : bars) b.sorted = true;
                flashIndex = -1;
                flashProgress = 0f;
                lastRunTimeMs = System.currentTimeMillis() - start;
                timeLbl.setText("Last visual run: " + lastRunTimeMs + " ms");
                repaint();
            }
        }).start();
    }

    private void checkPaused() {
        synchronized (pauseLock) {
            while (paused && !singleStep) {
                try { pauseLock.wait(); } catch (InterruptedException ignored) {}
            }
            if (singleStep) {
                singleStep = false;
                paused = true;
                pauseBtn.setText("Resume");
            }
        }
    }

    private void resetBarXsToIndices() {
        for (int i = 0; i < barCount; i++) {
            bars[i].x = getBarTargetX(i);
            bars[i].sorted = false;
            bars[i].currentColor = BASE_COLOR;
        }
    }

    private void runSortVisual() throws InterruptedException {
        algorithm = (String) algoBox.getSelectedItem();
        switch (algorithm) {
            case "Bubble Sort" -> bubbleSortVisual();
            case "Selection Sort" -> selectionSortVisual();
            case "Insertion Sort" -> insertionSortVisual();
            case "Merge Sort" -> mergeSortVisual(0, barCount - 1);
            case "Quick Sort" -> quickSortVisual(0, barCount - 1);
            // case "Heap Sort" -> heapSortVisual(); // Removed
            default -> { /* Should not happen with updated JComboBox */ }
        }
    }

    private void pulseAtIndex(int idx) throws InterruptedException {
        if (idx < 0 || idx >= barCount) return;
        flashIndex = idx;
        flashing = true;
        double speed = getSpeedMultiplier();
        double baseFlashMs = BASE_SWAP_DURATION_MS * 0.9;
        double durationMs = Math.max(40, baseFlashMs / speed);
        int steps = Math.max(2, (int) Math.ceil(durationMs / FRAME_DELAY_MS));
        for (int s = 0; s <= steps; s++) {
            checkPaused();
            flashProgress = (float) s / steps;
            repaint();
            Thread.sleep(FRAME_DELAY_MS);
        }
        for (int s = steps; s >= 0; s--) {
            checkPaused();
            flashProgress = (float) s / steps;
            repaint();
            Thread.sleep(FRAME_DELAY_MS);
        }
        flashProgress = 0f;
        flashing = false;
        flashIndex = -1;
        repaint();
    }

    // smoothstep easing
    private double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private void animateSwapIndices(int i, int j) throws InterruptedException {
        pulseAtIndex(i);
        if (soundToggle.isSelected()) playFlashSound();

        double targetI = getBarTargetX(j);
        double targetJ = getBarTargetX(i);

        Bar bi = bars[i];
        Bar bj = bars[j];

        activeIndex = i;
        comparedIndex = j;
        specialIndex = -1;
        repaint();

        if (soundToggle.isSelected()) playCompareSound();

        double speed = getSpeedMultiplier();
        double durationMs = Math.max(20, BASE_SWAP_DURATION_MS / speed);
        int steps = Math.max(2, (int) Math.ceil(durationMs / FRAME_DELAY_MS));

        double startXi = bi.x;
        double startXj = bj.x;

        for (int step = 1; step <= steps; step++) {
            checkPaused();
            double rawT = (double) step / steps;
            double t = smoothstep(rawT);
            bi.x = startXi + (targetI - startXi) * t;
            bj.x = startXj + (targetJ - startXj) * t;
            repaint();
            Thread.sleep(FRAME_DELAY_MS);
        }
        bi.x = targetI;
        bj.x = targetJ;

        Bar tmp = bars[i];
        bars[i] = bars[j];
        bars[j] = tmp;

        // after swapping objects, ensure their x positions match their new logical indices
        bars[i].x = getBarTargetX(i);
        bars[j].x = getBarTargetX(j);

        if (soundToggle.isSelected()) playSwapSound();

        activeIndex = comparedIndex = -1;
        repaint();
        Thread.sleep(Math.max(5, FRAME_DELAY_MS));
    }

    private void bubbleSortVisual() throws InterruptedException {
        for (int i = 0; i < barCount - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < barCount - i - 1; j++) {
                checkPaused();
                activeIndex = j;
                comparedIndex = j + 1;
                repaint();
                Thread.sleep(Math.max(8, (int)(BASE_SWAP_DURATION_MS / getSpeedMultiplier() / 6)));
                if (bars[j].value > bars[j + 1].value) {
                    animateSwapIndices(j, j + 1);
                    swapped = true;
                }
                activeIndex = comparedIndex = -1;
            }
            bars[barCount - i - 1].sorted = true;
            if (!swapped) break;
        }
    }

    private void selectionSortVisual() throws InterruptedException {
        for (int i = 0; i < barCount - 1; i++) {
            int minIdx = i;
            specialIndex = i;
            for (int j = i + 1; j < barCount; j++) {
                checkPaused();
                activeIndex = minIdx;
                comparedIndex = j;
                repaint();
                Thread.sleep(Math.max(8, (int)(BASE_SWAP_DURATION_MS / getSpeedMultiplier() / 6)));
                if (bars[j].value < bars[minIdx].value) {
                    minIdx = j;
                    specialIndex = minIdx;
                }
            }
            if (minIdx != i) animateSwapIndices(i, minIdx);
            bars[i].sorted = true;
            activeIndex = comparedIndex = specialIndex = -1;
        }
    }

    private void insertionSortVisual() throws InterruptedException {
        for (int i = 1; i < barCount; i++) {
            int j = i - 1;
            while (j >= 0 && bars[j].value > bars[j + 1].value) {
                checkPaused();
                animateSwapIndices(j, j + 1);
                j--;
            }
            bars[i].sorted = true;
            activeIndex = comparedIndex = -1;
        }
    }

    private void mergeSortVisual(int l, int r) throws InterruptedException {
        if (l >= r) return;
        int m = (l + r) / 2;
        mergeSortVisual(l, m);
        mergeSortVisual(m + 1, r);
        mergePlacesVisual(l, m, r);
    }

    private void mergePlacesVisual(int l, int m, int r) throws InterruptedException {
        int n = r - l + 1;
        Bar[] temp = new Bar[n];
        int i = l, j = m + 1, k = 0;

        while (i <= m && j <= r) {
            checkPaused();
            activeIndex = i;
            comparedIndex = j;
            repaint();
            Thread.sleep(Math.max(8, (int)(BASE_SWAP_DURATION_MS / getSpeedMultiplier() / 6)));
            if (bars[i].value <= bars[j].value) temp[k++] = bars[i++];
            else temp[k++] = bars[j++];
        }
        while (i <= m) temp[k++] = bars[i++];
        while (j <= r) temp[k++] = bars[j++];

        for (int idx = 0; idx < n; idx++) {
            int targetIndex = l + idx;
            Bar moving = temp[idx];
            int currentIndex = -1;
            for (int t = 0; t < barCount; t++) if (bars[t] == moving) currentIndex = t;
            if (currentIndex == -1) continue;

            pulseAtIndex(currentIndex);
            if (soundToggle.isSelected()) playFlashSound();

            double targetX = getBarTargetX(targetIndex);
            double durationMs = Math.max(20, BASE_SWAP_DURATION_MS / getSpeedMultiplier());
            int steps = Math.max(1, (int) Math.ceil(durationMs / FRAME_DELAY_MS));

            activeIndex = currentIndex;
            comparedIndex = targetIndex;

            double start = moving.x;
            for (int step = 1; step <= steps; step++) {
                checkPaused();
                double t = smoothstep((double) step / steps);
                moving.x = start + (targetX - start) * t;
                repaint();
                Thread.sleep(FRAME_DELAY_MS);
            }
            moving.x = targetX;

            if (currentIndex < targetIndex) {
                for (int s = currentIndex; s < targetIndex; s++) bars[s] = bars[s + 1];
                bars[targetIndex] = moving;
            } else if (currentIndex > targetIndex) {
                for (int s = currentIndex; s > targetIndex; s--) bars[s] = bars[s - 1];
                bars[targetIndex] = moving;
            }

            if (l == 0 && r == barCount - 1) bars[targetIndex].sorted = true;
            activeIndex = comparedIndex = -1;
            repaint();
            Thread.sleep(Math.max(5, FRAME_DELAY_MS));
        }
    }

    private void quickSortVisual(int l, int r) throws InterruptedException {
        if (l >= r) return;
        int pi = partitionVisual(l, r);
        quickSortVisual(l, pi - 1);
        quickSortVisual(pi + 1, r);
    }

    private int partitionVisual(int l, int r) throws InterruptedException {
        int pivotVal = bars[r].value;
        specialIndex = r;
        int i = l - 1;
        for (int j = l; j <= r - 1; j++) {
            checkPaused();
            activeIndex = j;
            comparedIndex = r;
            repaint();
            Thread.sleep(Math.max(8, (int)(BASE_SWAP_DURATION_MS / getSpeedMultiplier() / 6)));
            if (bars[j].value < pivotVal) {
                i++;
                animateSwapIndices(i, j);
            }
        }
        animateSwapIndices(i + 1, r);
        bars[i + 1].sorted = false;
        specialIndex = -1;
        return i + 1;
    }

    // Removed heapSortVisual and heapifyVisual methods here

    private void playFlashSound() {
        soundExec.execute(() -> playTone(1200, 0.03f, 0.05f));
    }

    private void playCompareSound() {
        soundExec.execute(() -> playTone(900, 0.04f, 0.06f));
    }

    private void playSwapSound() {
        soundExec.execute(() -> playTone(600, 0.06f, 0.09f));
    }

    private void playTone(double freq, double durationSec, double envelope) {
        final float SAMPLE_RATE = 44100f;
        int samples = (int) (SAMPLE_RATE * durationSec);
        byte[] buf = new byte[samples * 2];
        double twoPiF = 2 * Math.PI * freq;
        for (int i = 0; i < samples; i++) {
            double t = i / SAMPLE_RATE;
            double raw = Math.sin(twoPiF * t);
            double env;
            double attack = envelope;
            double decay = durationSec - attack;
            if (t < attack) env = t / attack;
            else if (decay > 0) env = Math.max(0.0, 1.0 - (t - attack) / decay);
            else env = 1.0;
            short val = (short) (raw * env * 0.55 * Short.MAX_VALUE);
            ByteBuffer.wrap(buf, i * 2, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(val);
        }

        AudioFormat af = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine sdl = AudioSystem.getSourceDataLine(af)) {
            sdl.open(af);
            sdl.start();
            sdl.write(buf, 0, buf.length);
            sdl.drain();
            sdl.stop();
        } catch (LineUnavailableException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TestFinal - Sorting Visual Demo");
            TestFinal visualizer = new TestFinal();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(visualizer);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            visualizer.resetBarXsToIndices();
            visualizer.repaint();
        });
    }
}
