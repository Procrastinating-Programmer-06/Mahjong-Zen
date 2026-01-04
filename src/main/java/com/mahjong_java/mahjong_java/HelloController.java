package com.mahjong_java.mahjong_java;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jpro.webapi.WebAPI;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import java.io.*;
import java.util.*;

public class HelloController {
    @FXML private BorderPane mainContainer;
    @FXML private StackPane gameArea;
    @FXML private ComboBox<String> layoutSelector;
    @FXML private Group rootPane;
    @FXML private Group zoomGroup;
    @FXML private Label percentageLabel;
    @FXML private VBox loadingOverlay;
    @FXML private Label loadingLabel;
    @FXML private StackPane windowRoot;
    @FXML private Group globalScaleGroup;

    // --- GLOBAL SPACING CONSTANTS (Ensures no misalignment) ---
    // Tracks if the user is on a mobile device to disable heavy effects
    private boolean isLowPerformanceMode = false;

    // Tracks the "design resolution" for your scaling math
    private static final double DESIGN_WIDTH = 1280.0;
    private static final double DESIGN_HEIGHT = 800.0;
    private static final double TILE_W = 60.0;
    private static final double TILE_H = 84.0;
    private static final double GAP = 15.0;
    private static final double Z_OFFSET = 5.0;
    private static final double PADDING = 50.0;

    private int removedPairs = 0;
    private int TOTAL_PAIRS;

    private static final int WIDTH = 24;
    private static final int HEIGHT = 14;
    private static final int LAYERS = 7;

    private MahjongTile[][][] logicBoard = new MahjongTile[WIDTH][HEIGHT][LAYERS];
    private MahjongTile firstSelected = null;
    private static final String SAVE_FILE = System.getProperty("user.home") + File.separator + "mahjong_zen_save.json";

    @FXML
    private boolean isLoadingFromSave = false;

    @FXML
    public void initialize() {
        gameArea.setStyle("-fx-background-color: #11111b;");
        StackPane.setAlignment(zoomGroup, javafx.geometry.Pos.CENTER);

        layoutSelector.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) {
                    setText(item);
                    setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                }
            }
        });

        layoutSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal) && !isLoadingFromSave) {
                runGameAction("LOADING " + newVal.toUpperCase() + "...", () -> {
                    Platform.runLater(this::setupGame);
                });
            }
        });

        File file = new File(SAVE_FILE);
        if (file.exists() && file.length() > 0) {
            isLoadingFromSave = true;
            loadGame();
            isLoadingFromSave = false;
        } else {
            setupGame();
        }

        gameArea.widthProperty().addListener((obs, oldVal, newVal) -> applySimpleResizing());
        gameArea.heightProperty().addListener((obs, oldVal, newVal) -> applySimpleResizing());
        Platform.runLater(this::applySimpleResizing);
    }
    private void applyGlobalScaling() {
        // These must match your prefWidth/Height in FXML
        double designWidth = 1280;
        double designHeight = 800;

        double windowWidth = windowRoot.getWidth();
        double windowHeight = windowRoot.getHeight();

        if (windowWidth > 0 && windowHeight > 0) {
            double scaleX = windowWidth / designWidth;
            double scaleY = windowHeight / designHeight;

            // SMART SCALING:
            // If the window is TALL (Portrait/Phone), we fit to WIDTH so it's big enough to play.
            // If the window is WIDE (Landscape/PC), we fit to the smallest dimension (Math.min).
            double finalScale = (windowHeight > windowWidth) ? scaleX : Math.min(scaleX, scaleY);

            globalScaleGroup.getTransforms().setAll(
                    new javafx.scene.transform.Scale(finalScale, finalScale, 0, 0)
            );

            // Center the board
            double totalScaledWidth = designWidth * finalScale;
            double totalScaledHeight = designHeight * finalScale;

            globalScaleGroup.setTranslateX((windowWidth - totalScaledWidth) / 2);
            globalScaleGroup.setTranslateY((windowHeight - totalScaledHeight) / 2);
        }
    }
    private void runGameAction(String message, Runnable action) {
        loadingOverlay.setVisible(true);
        loadingLabel.setText(message);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Execute the shuffle/restart/layout logic
                action.run();
                // A tiny 300ms pause so the gold loader doesn't "flicker" too fast
                Thread.sleep(300);
                return null;
            }
        };

        task.setOnSucceeded(e -> loadingOverlay.setVisible(false));
        task.setOnFailed(e -> {
            loadingOverlay.setVisible(false);
            e.getSource().getException().printStackTrace();
        });

        new Thread(task).start();
    }


    private void setupGame() {
        new File(SAVE_FILE).delete();
        rootPane.getChildren().clear();
        logicBoard = new MahjongTile[WIDTH][HEIGHT][LAYERS];
        firstSelected = null;

        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(event -> {
            List<int[]> slots = getSelectedLayoutSlots();

            // 1. FIXED PARITY: Ensure we have pairs
            if (slots.size() % 2 != 0) slots.remove(slots.size() - 1);

            // 2. FIXED PERCENTAGE: Set these BEFORE anything else
            this.TOTAL_PAIRS = slots.size() / 2;
            this.removedPairs = 0;

            List<String> pool = createSymbolPoolCustom(slots.size());

            // We don't shuffle slots anymore to keep the layout structure predictable
            // We only shuffle the pool of symbols
            List<TilePosition> plan = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                int[] s = slots.get(i);
                plan.add(new TilePosition(s[0], s[1], s[2], pool.get(i)));
            }

            plan.sort(Comparator.comparingInt(p -> p.z));

            // 3. FIXED GRID: Calculate bounds for the FULL layout once
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            for (TilePosition p : plan) {
                double posX = (p.x * (TILE_W + GAP)) - (p.z * Z_OFFSET);
                double posY = (p.y * (TILE_H + GAP)) - (p.z * Z_OFFSET);
                minX = Math.min(minX, posX);
                minY = Math.min(minY, posY);
            }

            List<MahjongTile> tiles = new ArrayList<>();
            for (TilePosition p : plan) {
                MahjongTile tile = new MahjongTile(p.symbol, this);
                tile.setGridCoordinates(p.x, p.y, p.z);
                logicBoard[p.x][p.y][p.z] = tile;

                // Use the calculated minX/minY to "Lock" the tile positions
                double posX = (p.x * (TILE_W + GAP)) - (p.z * Z_OFFSET);
                double posY = (p.y * (TILE_H + GAP)) - (p.z * Z_OFFSET);

                tile.setLayoutX(posX - minX + PADDING);
                tile.setLayoutY(posY - minY + PADDING);

                tile.setOpacity(0);
                tiles.add(tile);
                rootPane.getChildren().add(tile);
            }

            // 4. ANIMATION: Parallel fade-in
            ParallelTransition pt = new ParallelTransition();
            for (int i = 0; i < tiles.size(); i++) {
                FadeTransition ft = new FadeTransition(Duration.millis(300), tiles.get(i));
                ft.setToValue(1.0);
                ft.setDelay(Duration.millis(i * 1.5));
                pt.getChildren().add(ft);
            }
            pt.play();

            updateProgressLabel();
            updateAllTileStatuses();
            saveGame();

            // Use runLater to ensure the layout is finished before centering the zoomGroup
            Platform.runLater(this::applySimpleResizing);
        });
        pause.play();
    }

    public void handleTileClick(MahjongTile clickedTile) {
        if (firstSelected == null) {
            firstSelected = clickedTile;
            firstSelected.select();
        } else if (firstSelected == clickedTile) {
            // DESELECT logic
            firstSelected.deselect();
            firstSelected = null;
        } else {
            if (firstSelected.getSymbol().equals(clickedTile.getSymbol())) {
                removePair(firstSelected, clickedTile);
                firstSelected = null;
            } else {
                // FAILED MATCH logic
                firstSelected.deselect();
                firstSelected = null;
            }
        }
    }

    private void removePair(MahjongTile t1, MahjongTile t2) {
        logicBoard[t1.getGridX()][t1.getGridY()][t1.getGridZ()] = null;
        logicBoard[t2.getGridX()][t2.getGridY()][t2.getGridZ()] = null;
        rootPane.getChildren().removeAll(t1, t2);

        removedPairs++;
        updateProgressLabel();
        updateAllTileStatuses();
        saveGame();

        // Necessary call: Check if that was the last pair
        if (removedPairs >= TOTAL_PAIRS && TOTAL_PAIRS > 0) {
            checkGameCompletion();
        }
    }
    private void checkGameCompletion() {
        loadingOverlay.setVisible(true);
        loadingLabel.setText("VICTORY! GENERATING NEW BOARD...");

        // Brief pause so the player sees the 100% progress before the reset
        PauseTransition pause = new PauseTransition(Duration.seconds(2.0));
        pause.setOnFinished(e -> {
            loadingOverlay.setVisible(false);
            setupGame();
        });
        pause.play();
    }

    private void updateAllTileStatuses() {
        for (Node node : rootPane.getChildren()) {
            if (node instanceof MahjongTile t) {
                t.setBlocked(checkIfBlocked(t.getGridX(), t.getGridY(), t.getGridZ()));
            }
        }
    }

    private boolean checkIfBlocked(int x, int y, int z) {
        if (z < LAYERS - 1 && logicBoard[x][y][z + 1] != null) return true;
        boolean left = (x > 0 && logicBoard[x - 1][y][z] != null);
        boolean right = (x < WIDTH - 1 && logicBoard[x + 1][y][z] != null);
        return left && right;
    }

    // --- SHUFFLE (Fixed Misalignment) ---

    @FXML
    private void handleShuffle() {
        // 1. Clear selection using the correct variable: firstSelected
        if (firstSelected != null) {
            firstSelected.deselect();
            firstSelected = null;
        }

        // 2. Wrap the logic in the Loading UI
        runGameAction("SHUFFLING TILES...", () -> {
            // Collect existing tiles and symbols from the UI thread before background work
            final List<MahjongTile> activeTiles = new ArrayList<>();
            final List<String> symbols = new ArrayList<>();

            // We use a temporary list to avoid ConcurrentModificationException
            for (Node n : rootPane.getChildren()) {
                if (n instanceof MahjongTile t) {
                    activeTiles.add(t);
                    symbols.add(t.getSymbol());
                }
            }

            if (symbols.size() < 2) return;

            // Shuffle the symbols in the background
            Collections.shuffle(symbols);

            // Find current min bounds for centering
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            for (MahjongTile t : activeTiles) {
                double rawX = (t.getGridX() * (TILE_W + GAP)) - (t.getGridZ() * Z_OFFSET);
                double rawY = (t.getGridY() * (TILE_H + GAP)) - (t.getGridZ() * Z_OFFSET);
                minX = Math.min(minX, rawX);
                minY = Math.min(minY, rawY);
            }

            final double finalMinX = minX;
            final double finalMinY = minY;

            // Update the UI on the JavaFX Thread
            Platform.runLater(() -> {
                rootPane.getChildren().clear();
                logicBoard = new MahjongTile[WIDTH][HEIGHT][LAYERS];

                // Sort by Z to ensure correct 3D stacking order
                activeTiles.sort(Comparator.comparingInt(MahjongTile::getGridZ));

                ParallelTransition pt = new ParallelTransition();

                for (int i = 0; i < activeTiles.size(); i++) {
                    MahjongTile old = activeTiles.get(i);
                    int gx = old.getGridX();
                    int gy = old.getGridY();
                    int gz = old.getGridZ();

                    // Create the new tile with the shuffled symbol
                    MahjongTile replacement = new MahjongTile(symbols.get(i), this);
                    replacement.setGridCoordinates(gx, gy, gz);
                    replacement.setOpacity(0); // Set to 0 for fade-in effect

                    double posX = (gx * (TILE_W + GAP)) - (gz * Z_OFFSET);
                    double posY = (gy * (TILE_H + GAP)) - (gz * Z_OFFSET);

                    replacement.setLayoutX(posX - finalMinX + PADDING);
                    replacement.setLayoutY(posY - finalMinY + PADDING);

                    logicBoard[gx][gy][gz] = replacement;
                    rootPane.getChildren().add(replacement);

                    // Add fade-in animation to make the shuffle look smooth
                    FadeTransition ft = new FadeTransition(Duration.millis(200), replacement);
                    ft.setToValue(1.0);
                    pt.getChildren().add(ft);
                }

                pt.play();
                updateAllTileStatuses();
                saveGame();
            });
        });
    }

    // --- LAYOUT SLOTS ---

    private List<int[]> getSelectedLayoutSlots() {
        String layout = layoutSelector.getValue();
        if (layout == null) layout = "Turtle";
        return switch (layout) {
            case "Dragon" -> getDragonSlots();
            case "Fortress" -> getFortressSlots();
            case "Cloud" -> getCloudSlots();
            case "Overpass" -> getOverpassSlots();
            default -> getTurtleSlots();
        };
    }

    private List<int[]> getTurtleSlots() {
        List<int[]> s = new ArrayList<>();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 12; x++) {
            if ((x == 0 || x == 11) && (y == 0 || y == 7)) continue;
            s.add(new int[]{x + 2, y + 1, 0});
        }
        s.add(new int[]{1, 4, 0}); s.add(new int[]{1, 5, 0});
        s.add(new int[]{14, 4, 0}); s.add(new int[]{14, 5, 0});
        s.add(new int[]{0, 4, 0}); s.add(new int[]{15, 4, 0});
        for (int y = 1; y < 7; y++) for (int x = 3; x < 9; x++) s.add(new int[]{x + 2, y + 1, 1});
        for (int y = 2; y < 6; y++) for (int x = 4; x < 8; x++) s.add(new int[]{x + 2, y + 1, 2});
        for (int y = 3; y < 5; y++) for (int x = 5; x < 7; x++) s.add(new int[]{x + 2, y + 1, 3});
        s.add(new int[]{7, 4, 4}); s.add(new int[]{8, 4, 4});
        return s;
    }

    private List<int[]> getDragonSlots() {
        List<int[]> s = new ArrayList<>();

        // 1. THE SPINE (3 Layers high in the center)
        for (int x = 6; x < 18; x++) {
            s.add(new int[]{x, 5, 0}); // Base
            s.add(new int[]{x, 5, 1}); // Middle
            if (x > 8 && x < 15) {
                s.add(new int[]{x, 5, 2}); // Top Ridge
            }
        }

        // 2. THE BODY/SIDES (2 Layers high)
        for (int x = 7; x < 17; x++) {
            s.add(new int[]{x, 4, 0});
            s.add(new int[]{x, 4, 1});
            s.add(new int[]{x, 6, 0});
            s.add(new int[]{x, 6, 1});
        }

        // 3. THE LEGS / CLAWS (1 Layer - Spreading out)
        int[] legsX = {7, 10, 13, 16};
        for (int x : legsX) {
            s.add(new int[]{x, 3, 0}); // Upper legs
            s.add(new int[]{x, 7, 0}); // Lower legs
            s.add(new int[]{x - 1, 2, 0}); // Claws
            s.add(new int[]{x - 1, 8, 0});
        }

        // 4. THE HEAD (A blocky 2x2 area at the front)
        for (int z = 0; z < 3; z++) {
            s.add(new int[]{18, 4, z});
            s.add(new int[]{18, 5, z});
            s.add(new int[]{19, 4, z});
            s.add(new int[]{19, 5, z});
        }

        // 5. THE TAIL (Tapering off at the back)
        s.add(new int[]{5, 5, 0});
        s.add(new int[]{4, 5, 0});
        s.add(new int[]{3, 6, 0});

        return s;
    }

    private List<int[]> getFortressSlots() {
        List<int[]> s = new ArrayList<>();

        // 1. TALL TOWERS (Shifted inward to x=8 and x=13)
        // 4 Layers high to emphasize the 3D blocky look
        int[][] towers = {{8, 3}, {13, 3}, {8, 8}, {13, 8}};
        for (int[] pos : towers) {
            for (int z = 0; z < 4; z++) {
                s.add(new int[]{pos[0], pos[1], z});
                s.add(new int[]{pos[0] + 1, pos[1], z});
                s.add(new int[]{pos[0], pos[1] + 1, z});
                s.add(new int[]{pos[0] + 1, pos[1] + 1, z});
            }
        }

        // 2. CONNECTING WALLS (2 Layers high)
        // These link the towers into a square
        for (int x = 10; x < 13; x++) {
            s.add(new int[]{x, 3, 0}); s.add(new int[]{x, 3, 1}); // Top Wall
            s.add(new int[]{x, 9, 0}); s.add(new int[]{x, 9, 1}); // Bottom Wall
        }
        for (int y = 5; y < 8; y++) {
            s.add(new int[]{8, y, 0}); s.add(new int[]{8, y, 1}); // Left Wall
            s.add(new int[]{14, y, 0}); s.add(new int[]{14, y, 1}); // Right Wall
        }

        // 3. THE CENTER KEEP (3 Layers high)
        for (int z = 0; z < 3; z++) {
            s.add(new int[]{11, 6, z});
        }

        return s;
    }

    private List<int[]> getCloudSlots() {
        List<int[]> s = new ArrayList<>();
        for (int y = 4; y < 10; y++) for (int x = 6; x < 18; x++) s.add(new int[]{x, y, 0});
        for (int y = 5; y < 9; y++) for (int x = 8; x < 16; x++) s.add(new int[]{x, y, 1});
        return s;
    }

    private List<int[]> getOverpassSlots() {
        List<int[]> s = new ArrayList<>();

        // 1. THE LEFT AND RIGHT TOWERS (Anchor points)
        // Three layers thick to give it real weight
        for (int z = 0; z < 3; z++) {
            for (int y = 3; y < 9; y++) {
                for (int x = 3; x < 6; x++) s.add(new int[]{x, y, z});    // Left Tower
                for (int x = 18; x < 21; x++) s.add(new int[]{x, y, z}); // Right Tower
            }
        }

        // 2. THE BRIDGE DECK (Layer 1 - The Floor)
        // Connects the towers in the middle
        for (int x = 6; x < 18; x++) {
            for (int y = 5; y < 7; y++) s.add(new int[]{x, y, 1});
        }

        // 3. THE UPPER BRIDGE (Layer 2 - The Top Railing)
        // This overlaps the deck so you see the 'Bridge' effect clearly
        for (int x = 8; x < 16; x++) {
            s.add(new int[]{x, 5, 2});
            s.add(new int[]{x, 6, 2});
        }

        return s;
    }

    private List<String> createSymbolPoolCustom(int size) {
        List<String> pool = new ArrayList<>();
        String[] types = {"🀙", "🀚", "🀛", "🀜", "🀝", "🀞", "🀟", "🀠", "🀡", "🀐", "🀑", "🀒", "🀓", "🀔", "🀕", "🀖", "🀗", "🀘", "🀇", "🀈", "🀉", "🀊", "🀋", "🀌", "🀍", "🀎", "🀏", "🀀", "🀁", "🀂", "🀃", "🀢", "🀅", "🀆"};
        for (int i = 0; i < size / 2; i++) {
            String sym = types[i % types.length];
            pool.add(sym); pool.add(sym);
        }
        Collections.shuffle(pool);
        return pool;
    }

    // --- SAVE/LOAD (Synchronized) ---

    private void saveGame() {
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            GameSaveData data = new GameSaveData();

            // Save the current board state
            data.layout = layoutSelector.getValue();
            data.removedPairs = this.removedPairs;      // CRITICAL: Save current progress
            data.totalPairsAtStart = this.TOTAL_PAIRS;  // CRITICAL: Save the goalpost

            data.tiles = new ArrayList<>();
            for (Node n : rootPane.getChildren()) {
                if (n instanceof MahjongTile t) {
                    data.tiles.add(new TileSaveState(t.getGridX(), t.getGridY(), t.getGridZ(), t.getSymbol()));
                }
            }

            new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadGame() {
        try (FileReader reader = new FileReader(SAVE_FILE)) {
            GameSaveData data = new Gson().fromJson(reader, GameSaveData.class);
            layoutSelector.setValue(data.layout);
            this.removedPairs = data.removedPairs;
            this.TOTAL_PAIRS = data.totalPairsAtStart;

            rootPane.getChildren().clear();
            logicBoard = new MahjongTile[WIDTH][HEIGHT][LAYERS];

            // 1. Sort by Z to prevent Z-fighting
            data.tiles.sort(Comparator.comparingInt(t -> t.z));

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            for (TileSaveState ts : data.tiles) {
                MahjongTile t = new MahjongTile(ts.symbol, this);
                t.setGridCoordinates(ts.x, ts.y, ts.z);
                logicBoard[ts.x][ts.y][ts.z] = t;

                // USE SHARED MATH
                double posX = (ts.x * (TILE_W + GAP)) - (ts.z * Z_OFFSET);
                double posY = (ts.y * (TILE_H + GAP)) - (ts.z * Z_OFFSET);

                t.setLayoutX(posX);
                t.setLayoutY(posY);

                minX = Math.min(minX, posX);
                minY = Math.min(minY, posY);
                rootPane.getChildren().add(t);
            }

            for (Node n : rootPane.getChildren()) {
                n.setLayoutX(n.getLayoutX() - minX + PADDING);
                n.setLayoutY(n.getLayoutY() - minY + PADDING);
            }

            updateProgressLabel();
            updateAllTileStatuses();
            Platform.runLater(this::applySimpleResizing);

            // Inside loadGame() after the loop
            this.removedPairs = data.removedPairs;
            this.TOTAL_PAIRS = data.totalPairsAtStart;
            updateProgressLabel(); // Force the label to update

        } catch (Exception e) {
            setupGame();
        }
    }

    private void updateProgressLabel() {
        if (percentageLabel != null && TOTAL_PAIRS > 0) {
            percentageLabel.setText((int)(((double)removedPairs/TOTAL_PAIRS)*100) + "%");
        }
    }

    private void applySimpleResizing() {
        // 1. Get the current size of the window/container
        double containerW = gameArea.getWidth();
        double containerH = gameArea.getHeight();

        // 2. Get the exact area covered by the tiles (the "Layout Bounds")
        // Note: We use rootPane here because it contains the raw tiles
        javafx.geometry.Bounds bounds = rootPane.getLayoutBounds();
        double contentW = bounds.getWidth();
        double contentH = bounds.getHeight();

        if (contentW > 0 && contentH > 0 && containerW > 0 && containerH > 0) {
            // 3. Calculate scale to fit 90% of the screen (safety margin)
            double scaleX = (containerW * 0.90) / contentW;
            double scaleY = (containerH * 0.90) / contentH;

            // 4. Use the smaller of the two to maintain aspect ratio
            double finalScale = Math.min(scaleX, scaleY);

            // 5. Apply the scale to the zoomGroup
            zoomGroup.setScaleX(finalScale);
            zoomGroup.setScaleY(finalScale);

            // 6. Center the zoomGroup manually inside the gameArea
            zoomGroup.setTranslateX(0);
            zoomGroup.setTranslateY(0);
        }
    }

    @FXML
    private void handleReset() {
        // 1. Clear any active selection
        if (firstSelected != null) {
            firstSelected.deselect();
            firstSelected = null;
        }

        // 2. Trigger the Loading Screen
        runGameAction("RESTARTING BOARD...", () -> {
            // Since setupGame touches the UI, we must push it to the UI Thread
            Platform.runLater(this::setupGame);
        });
    }
    @FXML
    private void handleNewGame() {
        File file = new File(SAVE_FILE);
        if (file.exists()) {
            file.delete();
        }
        setupGame();
    }

    public static class GameSaveData { String layout; int removedPairs; int totalPairsAtStart; List<TileSaveState> tiles; }
    public static class TileSaveState { int x, y, z; String symbol; public TileSaveState(int x, int y, int z, String symbol) { this.x=x; this.y=y; this.z=z; this.symbol=symbol; } }
    public static class TilePosition { int x, y, z; String symbol; public TilePosition(int x, int y, int z, String symbol) { this.x=x; this.y=y; this.z=z; this.symbol=symbol; } }
}