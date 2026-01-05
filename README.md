# Mahjong-Zen
Simple casual game of Mahjong featuring progress saves, different layouts and a pleasant blocky aesthetic , all designed in Java.

Mahjong Zen is a personal project I created to practice building desktop apps with **Java** and **JavaFX**. It is a simple version of the classic Mahjong Solitaire game where you match pairs of tiles to clear the board.

### How the Code Works
I split the project into a few main parts to keep it organized:

* **The 3D View:** Instead of a flat 2D grid, I used a blocky view. This means the code draws tiles with a slight offset and a "side" texture so they look like they are actually stacked on top of each other.
* **The Layout Engine:** This part reads coordinates to place tiles. It handles different shapes like the **Dragon** and **Fortress** by telling the game which tiles are on Level 0, Level 1, and so on.
* **The Selection Logic:** This is the "brain" of the game. It checks if a tile is "blocked" (has something on top or on both sides) before letting you click it.
* **The Save System:** I used the **Gson** library to turn the game state into a small text file (`.json`). This keeps track of which tiles you already matched so you don't lose your spot when you close the app.

### Development Details
Developing this game involved solving a few interesting problems:

* **Layering Logic:** One of the main challenges was making sure the game correctly identifies which tiles are "covered." I implemented a check that looks at the Z-axis (height) and the X/Y coordinates to ensure a tile can only be clicked if it is physically reachable.
* **Asset Management:** I organized the project so that all tile images and layout files are loaded from the `resources` folder. This makes it easier to add new themes or board shapes without changing the core Java code.
* **Coordinate Mapping:** Since the game uses a custom view, I had to map mouse clicks to the specific depth of a tile. This ensures that when you click a "tall" stack, you select the top tile rather than the one underneath it.
* **State Persistence:** I focused on making the game "remember" its state. Even if the application is closed suddenly, the JSON save file ensures that the board remains exactly as you left it.

### Project Files
* `src/main/java`: Contains the logic for the game board, the tile rules, and the save system.
* `src/main/resources`: Contains the tile images and the background layout files.
* `pom.xml`: The Maven file that manages libraries like JavaFX and Gson.

### How to Run
1. Ensure you have **Java 23** or higher installed.
2. Download the `MahjongZen.jar` from the **Releases** tab on this repository.
3. Run it using your terminal:
   ```bash
   java -jar MahjongZen.jar

### Credits & Attributions

This project uses assets from the following sources:

* **Mahjong Tile Images:** [riichi-mahjong-tiles (Black Variant)](https://github.com/FluffyStuff/riichi-mahjong-tiles) by [@FluffyStuff](https://github.com/FluffyStuff)
* **App Icon:** [Mahjong Icon Pack](https://www.flaticon.com/free-icons/mahjong) by [Freepik](https://www.flaticon.com/authors/freepik) via Flaticon.
