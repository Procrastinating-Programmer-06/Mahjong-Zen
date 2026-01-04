package com.mahjong_java.mahjong_java;

import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeType;

public class MahjongTile extends StackPane {
    private String symbol;
    private boolean isBlocked = true;
    private int gridX, gridY, gridZ;
    private HelloController controller;
    private Rectangle base;
    private ImageView symbolView;

    public MahjongTile(String symbol, HelloController controller) {
        this.symbol = symbol;
        this.controller = controller;

        double w = 60;
        double h = 84;
        double thickness = 5;

        // 1. THE TILE BODY (3D Side Wall)
        Rectangle body = new Rectangle(w, h);
        body.setFill(Color.web("#2a2a2a"));
        body.setArcWidth(12);
        body.setArcHeight(12);
        body.setTranslateX(thickness);
        body.setTranslateY(thickness);

        // 2. THE TILE FACE
        this.base = new Rectangle(w, h);
        this.base.setFill(Color.web("#121212"));
        this.base.setArcWidth(10);
        this.base.setArcHeight(10);
        this.base.setStroke(Color.web("#B0B0B0")); // Bright grey border
        this.base.setStrokeWidth(1.2);
        this.base.setStrokeType(StrokeType.INSIDE);

        // 3. IMAGE LOADING
        try {
            String fileName = mapSymbolToFileName(symbol);
            String fullPath = "/com/mahjong_java/mahjong_java/Images/Black_PNG/" + fileName;

            try (var is = getClass().getResourceAsStream(fullPath)) {
                if (is != null) {
                    Image img = new Image(is);
                    this.symbolView = new ImageView(img);
                    this.symbolView.setFitWidth(w * 0.60);
                    this.symbolView.setFitHeight(h * 0.60);
                    this.symbolView.setPreserveRatio(true);
                    this.symbolView.setSmooth(true);
                } else {
                    createFallbackText(symbol);
                }
            }
        } catch (Exception e) {
            createFallbackText(symbol);
        }

        // 4. ASSEMBLY
        this.getChildren().clear();
        this.getChildren().addAll(body, this.base);

        if (this.symbolView != null) {
            this.getChildren().add(this.symbolView);
            StackPane.setAlignment(this.symbolView, Pos.CENTER);
            this.symbolView.setTranslateX(-thickness/2);
            this.symbolView.setTranslateY(-thickness/2);
        }

        this.setManaged(false);
        this.setCache(true);
        this.setCacheHint(CacheHint.SPEED);

        this.setOnMousePressed(e -> {
            if (!this.isBlocked) {
                controller.handleTileClick(this);
            }
        });
    }

    public void select() {
        InnerShadow mildGlow = new InnerShadow();
        mildGlow.setRadius(15);
        mildGlow.setChoke(0.5);
        mildGlow.setColor(Color.rgb(255, 226, 125, 0.6)); // Mild Gold
        this.base.setEffect(mildGlow);
    }

    public void deselect() {
        this.base.setEffect(null);
    }

    // Mappings for symbols to images
    private String mapSymbolToFileName(String symbol) {
        switch (symbol) {
            case "🀙": return "Pin1.png"; case "🀚": return "Pin2.png"; case "🀛": return "Pin3.png";
            case "🀜": return "Pin4.png"; case "🀝": return "Pin5.png"; case "🀞": return "Pin6.png";
            case "🀟": return "Pin7.png"; case "🀠": return "Pin8.png"; case "🀡": return "Pin9.png";
            case "🀐": return "Sou1.png"; case "🀑": return "Sou2.png"; case "🀒": return "Sou3.png";
            case "🀓": return "Sou4.png"; case "🀔": return "Sou5.png"; case "🀕": return "Sou6.png";
            case "🀖": return "Sou7.png"; case "🀗": return "Sou8.png"; case "🀘": return "Sou9.png";
            case "🀇": return "Man1.png"; case "🀈": return "Man2.png"; case "🀉": return "Man3.png";
            case "🀊": return "Man4.png"; case "🀋": return "Man5.png"; case "🀌": return "Man6.png";
            case "🀍": return "Man7.png"; case "🀎": return "Man8.png"; case "🀏": return "Man9.png";
            case "🀀": return "Ton.png"; case "🀁": return "Nan.png"; case "🀂": return "Shaa.png";
            case "🀃": return "Pei.png"; case "🀅": return "Hatsu.png"; case "🀆": return "Man5-Dora.png";
            case "🀢": return "Chun.png";
            default: return "Pin1.png";
        }
    }

    private void createFallbackText(String symbol) {
        javafx.scene.text.Text text = new javafx.scene.text.Text(symbol);
        text.setFill(Color.WHITE);
        text.setStyle("-fx-font-size: 30px;");
        this.getChildren().add(text);
    }

    public void setGridCoordinates(int x, int y, int z) { this.gridX = x; this.gridY = y; this.gridZ = z; }
    public void setBlocked(boolean blocked) { this.isBlocked = blocked; }
    public String getSymbol() { return symbol; }
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public int getGridZ() { return gridZ; }
}