import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Random;

public class CheckersGui {

    private static final int TILE_SIZE = 75;
    private static final int BOARD_SIZE = 8;

    private static final Color DARK_TILE = Color.web("#6E8083");
    private static final Color LIGHT_TILE = Color.web("#8A9EA1");
    private static final Color PIECE_LIGHT = Color.web("#C0C0C0");
    private static final Color PIECE_DARK = Color.web("#444444");
    private static final Color HIGHLIGHT = Color.web("#FFD700");
    private static final Color VALID_MOVE = Color.web("#90EE90");
    private static final Color FORCED_CAPTURE = Color.web("#FF2222");

    private Label scoreLeft, scoreRight, scoreLabel, msgLabel, turnLabel, recordLabel;
    private Label myNameLabel, opponentNameLabel;
    private Label myRecordLabel, oppRecordLabel;
    private Label moveLogLabel;
    private TextArea messagesArea;
    private TextArea moveLogArea;
    private TextField chatField;
    private Button sendBtn, homeBtn, langBtn, titleBtn;
    private GridPane board;

    private StackPane boardStack;
    private VBox overlayBox;
    private Label overlayTitle, overlaySubtitle;
    private Button overlayQuit, overlayRematch;

    private int[][] pieces = new int[BOARD_SIZE][BOARD_SIZE];

    private int currentTurn = 1;
    private boolean myTurn = false;
    private int myPieceColor = 1;

    private int selectedRow = -1;
    private int selectedCol = -1;

    private int myScore = 0;
    private int opponentScore = 0;

    private Stage primaryStage;
    private String selectedLanguage;
    private String mode;

    private Runnable homeAction;
    private Runnable langAction;
    private Runnable disconnectAction;
    private Runnable rematchAction;
    private Consumer<Message> moveCallback;
    private Consumer<String> boardChatCallback;
    private Runnable gameOverCallback;
    private java.util.function.Consumer<Boolean> resultCallback;

    private boolean inChainJump = false;
    private int chainRow = -1;
    private int chainCol = -1;
    private boolean botInChain = false;

    private String myName = "You";
    private String opponentName = "Opponent";
    private int moveNumber = 0;
    private int sessionWins = 0;
    private int sessionLosses = 0;

    private StackPane[][] tileCache = new StackPane[BOARD_SIZE][BOARD_SIZE];

    public CheckersGui(Stage primaryStage, String selectedLanguage, String mode, Runnable homeAction, Runnable langAction, Runnable disconnectAction, Runnable rematchAction) {
        this.primaryStage = primaryStage;
        this.selectedLanguage = selectedLanguage;
        this.mode = mode;
        this.homeAction = homeAction;
        this.langAction = langAction;
        this.disconnectAction = disconnectAction;
        this.rematchAction = rematchAction;
        initPieces();
    }

    public void setMoveCallback(Consumer<Message> cb) {
        this.moveCallback = cb;
    }

    public void setBoardChatCallback(Consumer<String> cb) {
        this.boardChatCallback = cb;
    }

    public void setGameOverCallback(Runnable cb) {
        this.gameOverCallback = cb;
    }

    public void setResultCallback(java.util.function.Consumer<Boolean> cb) {
        this.resultCallback = cb;
    }

    public void setPlayerNames(String myName, String opponentName) {
        this.myName = (myName != null && !myName.isEmpty()) ? myName : "You";
        this.opponentName = (opponentName != null && !opponentName.isEmpty()) ? opponentName : "Opponent";
        updateNameLabels();
    }

    private void updateNameLabels() {
        if (myNameLabel != null) myNameLabel.setText(myName);
        if (opponentNameLabel != null) opponentNameLabel.setText(opponentName);
    }

    public void setRecord(int wins, int losses) {
        this.sessionWins = wins;
        this.sessionLosses = losses;
        boolean sp = selectedLanguage.equals("Spanish");
        String wLabel = sp ? "G" : "W";
        String lLabel = sp ? "P" : "L";
        if (mode.equals("MultiPlayer")) {
            if (myRecordLabel  != null) myRecordLabel.setText(wLabel + wins + " " + lLabel + losses);
            if (oppRecordLabel != null) oppRecordLabel.setText(wLabel + losses + " " + lLabel + wins);
        }
        else {
            if (recordLabel != null) recordLabel.setText(wLabel + wins + "  " + lLabel + losses);
        }
    }

    public void setMyPieceColor(int color) {
        this.myPieceColor = color;
        this.myTurn = (color == 1);
        updateTurnLabel();
        refreshBoard();
    }

    private String titleText() {
        if (mode.equals("MultiPlayer")) {
            return selectedLanguage.equals("Spanish") ? "MULTIJUGADOR" : "MULTIPLAYER";
        }
        return selectedLanguage.equals("Spanish") ? "UN JUGADOR" : "SINGLE PLAYER";
    }

    private void initPieces() {
        pieces = new int[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 != 0) {
                    pieces[row][col] = 2;
                }
            }
        }

        for (int row = 5; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 != 0) {
                    pieces[row][col] = 1;
                }
            }
        }
        currentTurn = 1;
        selectedRow = -1;
        selectedCol = -1;
        inChainJump = false;
        chainRow = -1;
        chainCol = -1;
    }

    public void resetBoard() {
        initPieces();
        myScore = 0;
        opponentScore = 0;
        myTurn = (myPieceColor == 1);
        moveNumber = 0;
        if (scoreLeft != null) scoreLeft.setText("0");
        if (scoreRight != null) scoreRight.setText("0");
        if (moveLogArea != null) moveLogArea.clear();
        refreshBoard();
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("board-root");
        root.setLeft(createSidePanel());
        root.setCenter(createBoardStack());
        if (mode.equals("MultiPlayer")) {
            root.setRight(createMoveLogPanel());
        }

        double sceneWidth = mode.equals("MultiPlayer") ? 1210 : 1000;
        Scene scene = new Scene(root, sceneWidth, 640);
        Font.loadFont(getClass().getResourceAsStream("/assets/Silkscreen/Silkscreen-Regular.ttf"), 12);
        Font.loadFont(getClass().getResourceAsStream("/assets/Silkscreen/Silkscreen-Bold.ttf"), 12);
        scene.getStylesheets().add(getClass().getResource("/assets/checkers.css").toExternalForm());
        refreshBoard();
        return scene;
    }

    private VBox createSidePanel() {
        titleBtn = new Button(titleText());
        titleBtn.getStyleClass().add("board-title-btn");

        scoreLabel = new Label(selectedLanguage.equals("Spanish") ? "PUNTUACIÓN" : "SCORE");
        scoreLabel.getStyleClass().add("board-label");

        scoreLeft = new Label("0");
        scoreRight = new Label("0");
        scoreLeft.getStyleClass().add("score-box");
        scoreRight.getStyleClass().add("score-box");

        HBox scoreRow = new HBox(10, scoreLeft, scoreRight);
        scoreRow.setAlignment(Pos.CENTER);

        VBox scoreBox;
        if (mode.equals("MultiPlayer")) {
            myNameLabel = new Label(myName);
            myNameLabel.getStyleClass().add("board-label");
            myNameLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #90EE90;");
            myNameLabel.setMaxWidth(85);
            myNameLabel.setAlignment(Pos.CENTER);

            opponentNameLabel = new Label(opponentName);
            opponentNameLabel.getStyleClass().add("board-label");
            opponentNameLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #FF9999;");
            opponentNameLabel.setMaxWidth(85);
            opponentNameLabel.setAlignment(Pos.CENTER);

            HBox nameRow = new HBox(10, myNameLabel, opponentNameLabel);
            nameRow.setAlignment(Pos.CENTER);

            myRecordLabel = new Label("W0 L0");
            myRecordLabel.getStyleClass().add("board-label");
            myRecordLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #90EE90;");
            myRecordLabel.setMaxWidth(85);
            myRecordLabel.setAlignment(Pos.CENTER);

            oppRecordLabel = new Label("W0 L0");
            oppRecordLabel.getStyleClass().add("board-label");
            oppRecordLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #FF9999;");
            oppRecordLabel.setMaxWidth(85);
            oppRecordLabel.setAlignment(Pos.CENTER);

            HBox recordRow = new HBox(10, myRecordLabel, oppRecordLabel);
            recordRow.setAlignment(Pos.CENTER);

            scoreBox = new VBox(2, scoreLabel, nameRow, recordRow, scoreRow);
        }
        else {
            recordLabel = new Label("W0  L0");
            recordLabel.getStyleClass().add("board-label");
            recordLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaddaa;");
            scoreBox = new VBox(4, scoreLabel, recordLabel, scoreRow);
        }
        scoreBox.getStyleClass().add("score-panel");
        scoreBox.setAlignment(Pos.CENTER);

        turnLabel = new Label();
        turnLabel.getStyleClass().add("board-label");
        updateTurnLabel();

        msgLabel = new Label(selectedLanguage.equals("Spanish") ? "MENSAJES" : "MESSAGES");
        msgLabel.getStyleClass().add("board-label");

        messagesArea = new TextArea();
        messagesArea.setEditable(false);
        messagesArea.setWrapText(true);
        messagesArea.getStyleClass().add("board-messages");
        messagesArea.setPrefHeight(120);

        chatField = new TextField();
        chatField.setPromptText(selectedLanguage.equals("Spanish") ? "Escribe..." : "Type...");
        chatField.getStyleClass().add("board-chat-field");

        sendBtn = new Button(selectedLanguage.equals("Spanish") ? "ENVIAR" : "SEND");
        sendBtn.getStyleClass().add("board-send-btn");
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.setOnAction(e -> {
            String text = chatField.getText().trim();
            if (!text.isEmpty()) {
                chatField.clear();
                if (boardChatCallback != null) {
                    boardChatCallback.accept(text);
                }
            }
        });
        chatField.setOnAction(e -> sendBtn.fire());

        homeBtn = new Button(selectedLanguage.equals("Spanish") ? "INICIO" : "HOME");
        homeBtn.getStyleClass().add("board-nav-btn");
        homeBtn.setOnAction(e -> showForfeitConfirmation());

        langBtn = new Button(selectedLanguage.equals("Spanish") ? "ING" : "LANG");
        langBtn.getStyleClass().add("board-nav-btn");
        langBtn.setOnAction(e -> { if (langAction != null) langAction.run(); });

        HBox navRow = new HBox(8, homeBtn, langBtn);
        navRow.setAlignment(Pos.CENTER);
        navRow.setPadding(new Insets(6, 0, 6, 0));

        VBox side;
        if (mode.equals("SinglePlayer")) {
            moveLogLabel = new Label(selectedLanguage.equals("Spanish") ? "REGISTRO" : "MOVE LOG");
            moveLogLabel.getStyleClass().add("board-label");

            moveLogArea = new TextArea();
            moveLogArea.setEditable(false);
            moveLogArea.setWrapText(true);
            moveLogArea.getStyleClass().add("board-messages");
            VBox.setVgrow(moveLogArea, Priority.ALWAYS);

            messagesArea = null;
            chatField = null;
            sendBtn = null;
            msgLabel = null;

            side = new VBox(8, titleBtn, scoreBox, turnLabel, moveLogLabel, moveLogArea, navRow);
        }
        else {
            side = new VBox(8, titleBtn, scoreBox, turnLabel, msgLabel, messagesArea, chatField, sendBtn, navRow);
        }

        side.getStyleClass().add("board-side-panel");
        side.setPrefWidth(200);
        side.setPadding(new Insets(10));
        side.setAlignment(Pos.TOP_CENTER);
        return side;
    }

    private VBox createMoveLogPanel() {
        boolean sp = selectedLanguage.equals("Spanish");

        moveLogLabel = new Label(sp ? "REGISTRO" : "MOVE LOG");
        moveLogLabel.getStyleClass().add("board-label");

        moveLogArea = new TextArea();
        moveLogArea.setEditable(false);
        moveLogArea.setWrapText(true);
        moveLogArea.getStyleClass().add("board-messages");
        VBox.setVgrow(moveLogArea, Priority.ALWAYS);

        VBox panel = new VBox(8, moveLogLabel, moveLogArea);
        panel.getStyleClass().add("board-side-panel");
        panel.setPrefWidth(200);
        panel.setPadding(new Insets(10));
        panel.setAlignment(Pos.TOP_CENTER);
        return panel;
    }

    private StackPane createBoardStack() {
        board = new GridPane();
        board.setStyle("-fx-border-width: 2; -fx-border-color: #333;");

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                StackPane tile = createTile(row, col);
                board.add(tile, col, row);
            }
        }

        overlayTitle = new Label();
        overlayTitle.getStyleClass().add("overlay-title");

        overlaySubtitle = new Label();
        overlaySubtitle.getStyleClass().add("overlay-subtitle");
        overlaySubtitle.setVisible(false);

        overlayQuit = new Button();
        overlayQuit.setId("quitBtn");
        overlayQuit.getStyleClass().add("overlay-button");
        overlayQuit.setOnAction(e -> {
            if (disconnectAction != null) disconnectAction.run();
        });

        overlayRematch = new Button();
        overlayRematch.setId("rematchBtn");
        overlayRematch.getStyleClass().add("overlay-button");
        overlayRematch.setOnAction(e -> {
            if (rematchAction != null) rematchAction.run();
        });

        overlayBox = new VBox(10);
        overlayBox.getStyleClass().add("overlay-box");
        overlayBox.setAlignment(Pos.CENTER);
        overlayBox.setVisible(false);

        boardStack = new StackPane(board, overlayBox);
        return boardStack;
    }

    private StackPane createTile(int row, int col) {
        Rectangle bg = new Rectangle(TILE_SIZE, TILE_SIZE);
        bg.setFill((row + col) % 2 == 0 ? LIGHT_TILE : DARK_TILE);

        StackPane tile = new StackPane();
        tile.setPrefSize(TILE_SIZE, TILE_SIZE);
        tile.getChildren().add(bg);

        Circle piece = new Circle(TILE_SIZE / 2.5);
        piece.setFill(Color.TRANSPARENT);
        tile.getChildren().add(piece);

        tile.setOnMouseClicked(e -> {
            if (myTurn && mode.equals("SinglePlayer")) {
                handleTileClick(row, col);
            }
            else if (myTurn && mode.equals("MultiPlayer")) {
                handleTileClick(row, col);
            }
        });

        tile.setUserData(new TileData(row, col, piece));
        tileCache[row][col] = tile;
        return tile;
    }

    private void handleTileClick(int row, int col) {
        if (inChainJump) {
            if (row == chainRow && col == chainCol) {
                highlightChainMoves(chainRow, chainCol);
            }
            else if (isValidCapture(chainRow, chainCol, row, col)) {
                executeMove(chainRow, chainCol, row, col, true);
            }
            return;
        }

        boolean captureForced = anyMyCaptureExists();

        if (selectedRow == -1) {
            if (isMyPiece(row, col)) {
                if (captureForced && !pieceHasCapture(row, col)) return;
                selectedRow = row;
                selectedCol = col;
                highlightValidMoves(row, col);
            }
        }
        else {
            if (row == selectedRow && col == selectedCol) {
                clearHighlight();
                selectedRow = -1;
                selectedCol = -1;
            }
            else if (isValidCapture(selectedRow, selectedCol, row, col)) {
                executeMove(selectedRow, selectedCol, row, col, true);
            }
            else if (!captureForced && isValidMove(selectedRow, selectedCol, row, col)) {
                executeMove(selectedRow, selectedCol, row, col, true);
            }
            else if (isMyPiece(row, col)) {
                if (captureForced && !pieceHasCapture(row, col)) return;
                clearHighlight();
                selectedRow = row;
                selectedCol = col;
                highlightValidMoves(row, col);
            }
            else {
                clearHighlight();
                selectedRow = -1;
                selectedCol = -1;
            }
        }
    }

    private void highlightChainMoves(int row, int col) {
        clearHighlight();
        colorTile(row, col, Color.web("#FF8C00"));
        int[] dRow = {-2, -2, 2, 2};
        int[] dCol = {-2,  2, -2, 2};
        for (int i = 0; i < 4; i++) {
            int tr = row + dRow[i], tc = col + dCol[i];
            if (isValidCapture(row, col, tr, tc)) {
                colorTile(tr, tc, VALID_MOVE);
            }
        }
    }

    private boolean isMyPiece(int row, int col) {
        int piece = pieces[row][col];
        if (piece == 0) return false;
        boolean isLight = (piece == 1 || piece == 3);
        return isLight == (myPieceColor == 1);
    }

    private void highlightValidMoves(int row, int col) {
        clearHighlight();
        boolean captureForced = anyMyCaptureExists();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (isValidCapture(row, col, r, c)) {
                    colorTile(r, c, VALID_MOVE);
                } else if (!captureForced && isValidMove(row, col, r, c)) {
                    colorTile(r, c, VALID_MOVE);
                }
            }
        }
        colorTile(row, col, HIGHLIGHT);
    }

    private boolean anyMyCaptureExists() {
        int[] dRow = {-2, -2, 2, 2};
        int[] dCol = {-2,  2, -2, 2};
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!isMyPiece(r, c)) continue;
                for (int i = 0; i < 4; i++) {
                    if (isValidCapture(r, c, r + dRow[i], c + dCol[i])) return true;
                }
            }
        }
        return false;
    }

    private boolean pieceHasCapture(int row, int col) {
        int[] dRow = {-2, -2, 2, 2};
        int[] dCol = {-2,  2, -2, 2};
        for (int i = 0; i < 4; i++) {
            if (isValidCapture(row, col, row + dRow[i], col + dCol[i])) return true;
        }
        return false;
    }

    private void clearHighlight() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                refreshTile(row, col);
            }
        }
        highlightForcedPieces();
    }

    private void colorTile(int row, int col, Color color) {
        StackPane tile = getTile(row, col);
        if (tile == null) return;
        Rectangle bg = (Rectangle) tile.getChildren().get(0);
        bg.setFill(color);
    }

    private void refreshTile(int row, int col) {
        StackPane tile = getTile(row, col);
        if (tile == null) return;

        Rectangle bg = (Rectangle) tile.getChildren().get(0);
        bg.setFill((row + col) % 2 == 0 ? LIGHT_TILE : DARK_TILE);
        Circle piece = (Circle) tile.getChildren().get(1);
        int p = pieces[row][col];
        piece.setFill(Color.TRANSPARENT);
        piece.setStroke(Color.TRANSPARENT);

        if (p == 1 || p == 3) {
            piece.setFill(PIECE_LIGHT);
        }
        else if (p == 2 || p == 4) {
            piece.setFill(PIECE_DARK);
        }

        if (p == 3 || p == 4) {
            piece.setStroke(Color.GOLD);
            piece.setStrokeWidth(3);
        }
    }

    private void highlightForcedPieces() {
        if (!myTurn) return;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (isMyPiece(r, c) && pieceHasCapture(r, c)) {
                    colorTile(r, c, FORCED_CAPTURE);
                }
            }
        }
    }

    private StackPane getTile(int row, int col) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return null;
        return tileCache[row][col];
    }

    private void refreshBoard() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                refreshTile(row, col);
            }
        }
        highlightForcedPieces();
    }

    private void logMove(boolean isMyMove, int fromRow, int fromCol, int toRow, int toCol, boolean wasCapture, boolean justKinged, boolean isChain) {
        if (moveLogArea == null) return;
        boolean sp = selectedLanguage.equals("Spanish");
        String who = isMyMove ? myName : opponentName;

        if (!isChain) moveNumber++;

        char fromColLetter = (char) ('A' + fromCol);
        char toColLetter = (char) ('A' + toCol);
        int  fromRowNum = BOARD_SIZE - fromRow;
        int  toRowNum = BOARD_SIZE - toRow;

        StringBuilder entry = new StringBuilder();
        if (!isChain) {
            entry.append("#").append(moveNumber).append(" ");
        } else {
            entry.append("   ↪ ");
        }
        entry.append(who).append(": ");
        entry.append(fromColLetter).append(fromRowNum);
        entry.append(" → ");
        entry.append(toColLetter).append(toRowNum);

        if (wasCapture) {
            entry.append(sp ? " captura" : " capture");
        }
        if (justKinged) {
            entry.append(sp ? " rey" : " king");
        }

        moveLogArea.appendText(entry.toString() + "\n");
    }

    private void executeMove(int fromRow, int fromCol, int toRow, int toCol, boolean sendToServer) {
        boolean wasCapture = false;
        boolean justKinged = false;

        if (Math.abs(toRow - fromRow) == 2) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;

            pieces[midRow][midCol] = 0;
            wasCapture = true;

            if (sendToServer) {
                myScore++;
            } else {
                opponentScore++;
            }

            if (scoreLeft != null) scoreLeft.setText(String.valueOf(myScore));
            if (scoreRight != null) scoreRight.setText(String.valueOf(opponentScore));
        }

        pieces[toRow][toCol] = pieces[fromRow][fromCol];
        pieces[fromRow][fromCol] = 0;

        if (pieces[toRow][toCol] == 1 && toRow == 0) {
            pieces[toRow][toCol] = 3; justKinged = true;
        }

        if (pieces[toRow][toCol] == 2 && toRow == BOARD_SIZE - 1) {
            pieces[toRow][toCol] = 4; justKinged = true;
        }

        selectedRow = -1;
        selectedCol = -1;

        logMove(sendToServer, fromRow, fromCol, toRow, toCol, wasCapture, justKinged, inChainJump);

        if (sendToServer && moveCallback != null) {
            moveCallback.accept(Message.gameMove(null, fromRow, fromCol, toRow, toCol));
        }

        if (sendToServer && wasCapture && !justKinged && canCaptureFrom(toRow, toCol)) {
            inChainJump = true;
            chainRow = toRow;
            chainCol = toCol;
            selectedRow = toRow;
            selectedCol = toCol;
            myTurn = true;
            updateTurnLabel();
            refreshBoard();
            highlightChainMoves(toRow, toCol);
            checkWinCondition();
        }
        else {
            inChainJump = false;
            chainRow = -1;
            chainCol = -1;
            myTurn = !sendToServer;
            currentTurn = (currentTurn == 1) ? 2 : 1;
            updateTurnLabel();
            refreshBoard();
            checkWinCondition();
            if (sendToServer && mode.equals("SinglePlayer")) {
                myTurn = false;
                updateTurnLabel();
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
                pause.setOnFinished(e -> doBotMove());
                pause.play();
            }
        }
    }

    public void applyOpponentMove(int fromRow, int fromCol, int toRow, int toCol) {
        executeMove(fromRow, fromCol, toRow, toCol, false);
        inChainJump = false;
        chainRow = -1;
        chainCol = -1;
        myTurn = true;
        updateTurnLabel();
        refreshBoard();
    }

    private boolean canCaptureFrom(int row, int col) {
        int[] dRow = {-2, -2, 2, 2};
        int[] dCol = {-2,  2, -2, 2};
        for (int i = 0; i < 4; i++) {
            if (isValidCapture(row, col, row + dRow[i], col + dCol[i])) return true;
        }
        return false;
    }

    private boolean isValidCapture(int fromRow, int fromCol, int toRow, int toCol) {
        if (toRow < 0 || toRow >= BOARD_SIZE || toCol < 0 || toCol >= BOARD_SIZE) return false;
        if (pieces[toRow][toCol] != 0) return false;
        if ((toRow + toCol) % 2 == 0) return false;
        int piece = pieces[fromRow][fromCol];
        if (piece == 0) return false;

        int rowDiff = toRow - fromRow;
        int colDiff = Math.abs(toCol - fromCol);
        if (Math.abs(rowDiff) != 2 || colDiff != 2) return false;

        boolean isKing  = (piece == 3 || piece == 4);
        boolean isLight = (piece == 1 || piece == 3);
        if (!isKing) {
            if (isLight  && rowDiff > 0) return false;
            if (!isLight && rowDiff < 0) return false;
        }

        int midRow = (fromRow + toRow) / 2;
        int midCol = (fromCol + toCol) / 2;
        int midPiece = pieces[midRow][midCol];
        if (midPiece == 0) return false;
        boolean midIsLight = (midPiece == 1 || midPiece == 3);
        return isLight != midIsLight;
    }

    private boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (toRow < 0 || toRow >= BOARD_SIZE || toCol < 0 || toCol >= BOARD_SIZE) return false;
        if (pieces[toRow][toCol] != 0) return false;
        if ((toRow + toCol) % 2 == 0) return false;

        int piece = pieces[fromRow][fromCol];
        if (piece == 0) return false;

        int colDiff = Math.abs(toCol - fromCol);
        int rowDiff = toRow - fromRow;
        boolean isKing  = (piece == 3 || piece == 4);
        boolean isLight = (piece == 1 || piece == 3);

        if (!isKing) {
            if (isLight  && rowDiff >= 0) return false;
            if (!isLight && rowDiff <= 0) return false;
        }

        if (Math.abs(rowDiff) == 1 && colDiff == 1) return true;

        if (Math.abs(rowDiff) == 2 && colDiff == 2) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            int midPiece = pieces[midRow][midCol];
            if (midPiece == 0) return false;
            boolean midIsLight = (midPiece == 1 || midPiece == 3);
            return isLight != midIsLight;
        }

        return false;
    }

    private void checkWinCondition() {
        int lightCount = 0, darkCount = 0;
        for (int[] row : pieces)
            for (int p : row) {
                if (p == 1 || p == 3) lightCount++;
                if (p == 2 || p == 4) darkCount++;
            }
        if (lightCount == 0 || darkCount == 0) {

            boolean myPieceWon = (myPieceColor == 1 && darkCount == 0) || (myPieceColor == 2 && lightCount == 0);

            if (myPieceWon) {
                sessionWins++;
            }
            else {
                sessionLosses++;
            }

            setRecord(sessionWins, sessionLosses);

            if (gameOverCallback != null) gameOverCallback.run();
            if (resultCallback != null) resultCallback.accept(myPieceWon);

            showGameOver(myPieceWon);
        }
    }

    public void showWaiting(int connected) {
        boolean sp = selectedLanguage.equals("Spanish");
        overlayTitle.setText(sp ? "ESPERANDO..." : "WAITING...");
        overlaySubtitle.setText(connected + "/2 " + (sp ? "CONECTADO" : "CONNECTED"));
        overlaySubtitle.setVisible(true);
        overlayQuit.setId("disconnectBtn");
        overlayQuit.setText(sp ? "DESCONECTAR" : "DISCONNECT");
        overlayBox.getChildren().setAll(overlayTitle, overlaySubtitle, overlayQuit);
        overlayBox.setVisible(true);
    }

    public void showGameOver(boolean won) {
        showGameOver(won, false);
    }

    public void showGameOver(boolean won, boolean opponentLeft) {
        boolean sp = selectedLanguage.equals("Spanish");
        overlayTitle.setText(won ? (sp ? "¡GANASTE!" : "YOU WIN!") : (sp ? "¡PERDISTE!" : "YOU LOSE!"));
        overlayQuit.setId("quitBtn");
        overlayQuit.setText(sp ? "SALIR" : "QUIT");

        HBox btnRow = new HBox(16);
        btnRow.setAlignment(Pos.CENTER);

        if (mode.equals("MultiPlayer")) {
            overlayQuit.setMinWidth(120);
            if (opponentLeft) {
                btnRow.getChildren().add(overlayQuit);
            }
            else {
                overlayRematch.setId("rematchBtn");
                overlayRematch.setText(sp ? "REVANCHA" : "REMATCH");
                overlayRematch.setMinWidth(120);
                btnRow.getChildren().addAll(overlayQuit, overlayRematch);
            }
        }
        else {
            overlayRematch.setId("rematchBtn");
            overlayRematch.setText(sp ? "JUGAR DE NUEVO" : "PLAY AGAIN");
            overlayQuit.setMinWidth(120);
            overlayRematch.setMinWidth(120);
            btnRow.getChildren().addAll(overlayQuit, overlayRematch);
        }

        overlayBox.getChildren().setAll(overlayTitle, btnRow);
        overlayBox.setVisible(true);
    }

    public void hideOverlay() {
        overlayBox.setVisible(false);
    }

    public void disableRematch() {
        boolean sp = selectedLanguage.equals("Spanish");
        for (int i = 0; i < overlayBox.getChildren().size(); i++) {
            if (overlayBox.getChildren().get(i) instanceof HBox) {
                Label noOppLabel = new Label(sp ? "El rival se fue." : "Opponent left.");
                noOppLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-family: 'Silkscreen'; -fx-font-size: 13px;");
                HBox btnRow = new HBox(16, overlayQuit, noOppLabel);
                btnRow.setAlignment(Pos.CENTER);
                overlayQuit.setId("quitBtn");
                overlayQuit.setText(sp ? "SALIR" : "QUIT");
                overlayQuit.setMinWidth(120);
                overlayBox.getChildren().set(i, btnRow);
                return;
            }
        }
        showGameOver(true, true);
    }

    public void applyLanguage(String lang) {
        this.selectedLanguage = lang;
        boolean sp = lang.equals("Spanish");
        titleBtn.setText(titleText());
        scoreLabel.setText(sp ? "PUNTUACIÓN" : "SCORE");
        if (msgLabel  != null) msgLabel.setText(sp ? "MENSAJES" : "MESSAGES");
        if (moveLogLabel != null) moveLogLabel.setText(sp ? "REGISTRO" : "MOVE LOG");
        if (sendBtn   != null) sendBtn.setText(sp ? "ENVIAR" : "SEND");
        homeBtn.setText(sp ? "INICIO" : "HOME");
        langBtn.setText(sp ? "ING" : "LANG");
        if (chatField != null) chatField.setPromptText(sp ? "Escribe..." : "Type...");
        updateTurnLabel();
        if (mode.equals("MultiPlayer")) {
            setRecord(sessionWins, sessionLosses);
        } else if (recordLabel != null) {
            String wLabel = sp ? "G" : "W";
            String lLabel = sp ? "P" : "L";
            recordLabel.setText(wLabel + sessionWins + "  " + lLabel + sessionLosses);
        }
        if (overlayBox != null && overlayBox.isVisible()) {
            String title = overlayTitle.getText();
            if (title.contains("WAITING") || title.contains("ESPERANDO")) {
                showWaiting(Integer.parseInt(overlaySubtitle.getText().charAt(0) + ""));
            }
        }
    }

    private void showForfeitConfirmation() {
        boolean sp = selectedLanguage.equals("Spanish");

        Stage dialog = new Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setTitle(sp ? "Confirmar" : "Confirm");
        dialog.setResizable(false);

        Label question = new Label(sp ? "¿Seguro que quieres rendirte y salir?" : "Are you sure you want to forfeit and go home?");
        question.setWrapText(true);
        question.setStyle("-fx-font-family: 'Silkscreen'; -fx-font-size: 13px; -fx-text-fill: #e0e0e0; -fx-text-alignment: center;");

        Button confirmBtn = new Button(sp ? "SÍ, SALIR" : "YES, LEAVE");
        confirmBtn.getStyleClass().add("overlay-button");
        confirmBtn.setId("quitBtn");
        confirmBtn.setOnAction(e -> {
            dialog.close();
            sessionLosses++;
            setRecord(sessionWins, sessionLosses);

            if (homeAction != null) homeAction.run();
        });

        Button cancelBtn = new Button(sp ? "CANCELAR" : "CANCEL");
        cancelBtn.getStyleClass().add("overlay-button");
        cancelBtn.setId("rematchBtn");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox btnRow = new HBox(16, confirmBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER);

        VBox layout = new VBox(20, question, btnRow);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30, 40, 30, 40));
        layout.setStyle("-fx-background-color: #1e2a2c;");

        dialog.setScene(new Scene(layout, 380, 150));
        try {
            dialog.getScene().getStylesheets().add(getClass().getResource("/assets/checkers.css").toExternalForm());
        } catch (Exception ignored) {}

        dialog.showAndWait();
    }

    private void updateTurnLabel() {
        if (turnLabel == null) return;
        boolean sp = selectedLanguage.equals("Spanish");
        if (myTurn) {
            turnLabel.setText(sp ? "▶ TU TURNO" : "▶ YOUR TURN");
            turnLabel.setStyle("-fx-text-fill: #90EE90; -fx-font-weight: bold;");
        }
        else {
            turnLabel.setText(sp ? "ESPERA..." : "WAITING...");
            turnLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-weight: bold;");
        }
    }

    public void appendMessage(String line) {
        if (messagesArea != null) messagesArea.appendText(line + "\n");
    }

    public void updateOpponentScore(int score) {
        opponentScore = score;
        if (scoreRight != null) scoreRight.setText(String.valueOf(opponentScore));
    }

    private void doBotMove() {
        int[] dRow = {-2, -2, 2, 2};
        int[] dCol = {-2,  2, -2, 2};
        int[] mRow = {-1, -1, 1, 1};
        int[] mCol = {-1,  1, -1, 1};

        ArrayList<int[]> captures = new ArrayList<>();
        ArrayList<int[]> moves    = new ArrayList<>();

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int p = pieces[r][c];
                if (p != 2 && p != 4) continue;
                for (int i = 0; i < 4; i++) {
                    if (isValidCapture(r, c, r + dRow[i], c + dCol[i]))
                        captures.add(new int[]{r, c, r + dRow[i], c + dCol[i]});
                    if (isValidMove(r, c, r + mRow[i], c + mCol[i]))
                        moves.add(new int[]{r, c, r + mRow[i], c + mCol[i]});
                }
            }
        }

        ArrayList<int[]> choices = captures.isEmpty() ? moves : captures;
        if (choices.isEmpty()) return;

        int[] pick = choices.get(new Random().nextInt(choices.size()));
        executeBotMove(pick[0], pick[1], pick[2], pick[3]);
    }

    private void executeBotMove(int fromRow, int fromCol, int toRow, int toCol) {
        boolean wasCapture = Math.abs(toRow - fromRow) == 2;
        boolean justKinged = false;

        if (wasCapture) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            int capturedPiece = pieces[midRow][midCol];
            pieces[midRow][midCol] = 0;
            if (capturedPiece == 1 || capturedPiece == 3) opponentScore++;
            else if (capturedPiece == 2 || capturedPiece == 4) myScore++;
            if (scoreLeft  != null) scoreLeft.setText(String.valueOf(myScore));
            if (scoreRight != null) scoreRight.setText(String.valueOf(opponentScore));
        }

        pieces[toRow][toCol] = pieces[fromRow][fromCol];
        pieces[fromRow][fromCol] = 0;

        if (pieces[toRow][toCol] == 2 && toRow == BOARD_SIZE - 1) {
            pieces[toRow][toCol] = 4; justKinged = true;
        }

        logMove(false, fromRow, fromCol, toRow, toCol, wasCapture, justKinged, botInChain);
        botInChain = false;

        currentTurn = (currentTurn == 1) ? 2 : 1;
        refreshBoard();
        checkWinCondition();

        if (wasCapture && !justKinged && canCaptureBotFrom(toRow, toCol)) {
            int[] dRow = {-2, -2, 2, 2};
            int[] dCol = {-2,  2, -2, 2};
            ArrayList<int[]> chain = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int nr = toRow + dRow[i], nc = toCol + dCol[i];
                if (isValidCapture(toRow, toCol, nr, nc))
                    chain.add(new int[]{toRow, toCol, nr, nc});
            }
            if (!chain.isEmpty()) {
                int[] next = chain.get(new Random().nextInt(chain.size()));
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
                pause.setOnFinished(e -> { botInChain = true; executeBotMove(next[0], next[1], next[2], next[3]); });
                pause.play();
                return;
            }
        }

        myTurn = true;
        updateTurnLabel();
        refreshBoard();
    }

    private boolean canCaptureBotFrom(int row, int col) {
        int[] dRow = {-2, -2, 2, 2};
        int[] dCol = {-2,  2, -2, 2};
        for (int i = 0; i < 4; i++) {
            if (isValidCapture(row, col, row + dRow[i], col + dCol[i])) return true;
        }
        return false;
    }

    private static class TileData {
        int row, col;
        Circle piece;
        TileData(int row, int col, Circle piece) {
            this.row = row;
            this.col = col;
            this.piece = piece;
        }
    }
}