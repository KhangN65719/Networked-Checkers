import java.util.HashMap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GuiClient extends Application {

    TextField messageField, usernameField;
    Button sendBtn, signInBtn, singlePlayer, language, multiPlayer, messaging;
    VBox mainVBox, playButtonVBox;
    HBox playButtonHbox;
    HashMap<String, Scene> sceneMap;
    Client clientConnection;
    String myUsername = null;
    String selectedLanguage = "English";
    String opponentUsername = null;
    Stage primaryStage;

    ListView<String> chatList;

    ToggleGroup targetGroup;
    RadioButton rbAll, rbPrivate, rbGroup;

    ComboBox<String> userCombo = new ComboBox<>();
    ComboBox<String> groupCombo = new ComboBox<>();

    TextField groupNameField;

    Label errorLabel, greeting;
    ImageView checkersIcon;

    CheckersGui activeBoard = null;
    int myPieceColor = 1;
    int sessionWins = 0;
    int sessionLosses = 0;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        chatList = new ListView<>();
        usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        signInBtn = new Button("Sign In");
        messageField = new TextField();
        messageField.setPromptText("Type a message...");
        sendBtn = new Button("Send");

        singlePlayer = new Button("Single Player");
        singlePlayer.getStyleClass().add("btn-red");
        messaging = new Button("Message");
        messaging.getStyleClass().add("btn-dark");
        language = new Button("Language");
        language.getStyleClass().add("btn-dark");
        multiPlayer = new Button("Multiplayer");
        multiPlayer.getStyleClass().add("btn-red");

        checkersIcon = new ImageView(getClass().getResource("/assets/checkersIcon.png").toExternalForm());
        checkersIcon.setFitWidth(140);
        checkersIcon.setFitHeight(140);
        checkersIcon.setPreserveRatio(true);

        greeting = new Label();
        greeting.getStyleClass().add("greeting");

        playButtonHbox = new HBox(20, singlePlayer, messaging, language, multiPlayer);
        playButtonHbox.getStyleClass().add("play-buttons-hbox");
        playButtonVBox = new VBox(playButtonHbox);
        playButtonVBox.getStyleClass().add("play-panel");
        mainVBox = new VBox(150, greeting, checkersIcon, playButtonVBox);
        mainVBox.getStyleClass().add("main-root");

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setVisible(false);

        signInBtn.setOnAction(e -> attemptSignIn());
        usernameField.setOnAction(e -> attemptSignIn());
        sendBtn.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        language.setOnAction(e -> showLanguagePopup());
        messaging.setOnAction(e -> showChatPopup());
        singlePlayer.setOnAction(e -> launchBoard("SinglePlayer"));
        multiPlayer.setOnAction(e -> launchBoard("MultiPlayer"));

        sceneMap = new HashMap<>();
        sceneMap.put("mainScene", createMainScreenGui());
        sceneMap.put("signIn", createLoginGui());

        primaryStage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        primaryStage.setScene(sceneMap.get("signIn"));
        primaryStage.setTitle("Checkers");
        primaryStage.show();
    }

    private void launchBoard(String gameMode) {
        activeBoard = new CheckersGui(
                // Constructor arguments
                primaryStage,
                selectedLanguage,
                gameMode,
                () -> {
                    if (gameMode.equals("MultiPlayer")) {
                        clientConnection.send(Message.forfeit(myUsername));
                    }
                    activeBoard = null;
                    opponentUsername = null;
                    primaryStage.setScene(sceneMap.get("mainScene"));
                },
                () -> {
                    showLanguagePopup();
                    if (activeBoard != null) activeBoard.applyLanguage(selectedLanguage);
                },
                () -> {
                    if (gameMode.equals("MultiPlayer")) {
                        clientConnection.send(Message.forfeit(myUsername));
                    }
                    clientConnection.send(Message.disconnectQueue(myUsername));
                    activeBoard = null;
                    opponentUsername = null;
                    primaryStage.setScene(sceneMap.get("mainScene"));
                },
                () -> {
                    if (activeBoard != null) {
                        activeBoard.hideOverlay();
                        activeBoard.resetBoard();
                        if (gameMode.equals("MultiPlayer")) {
                            clientConnection.send(Message.playAgain(myUsername));
                            activeBoard.showWaiting(1);
                        }
                    }
                }
        );

        if (gameMode.equals("SinglePlayer")) {
            activeBoard.setMoveCallback(null);
            activeBoard.setBoardChatCallback(null);
            activeBoard.setGameOverCallback(null);
            activeBoard.setPlayerNames(myUsername, "Bot");
            activeBoard.setResultCallback(won -> {
                if (won) sessionWins++; else sessionLosses++;
                activeBoard.setRecord(sessionWins, sessionLosses);
            });
        } else {
            activeBoard.setMoveCallback(msg ->
                    clientConnection.send(Message.gameMove(myUsername, msg.fromRow, msg.fromCol, msg.toRow, msg.toCol))
            );

            activeBoard.setGameOverCallback(() -> {
                Message go = new Message();
                go.type = Message.GAME_OVER;
                go.sender = myUsername;
                clientConnection.send(go);
            });

            activeBoard.setBoardChatCallback(text ->
                    clientConnection.send(Message.sendPrivate(myUsername, opponentUsername, text))
            );
        }

        primaryStage.setScene(activeBoard.createScene());

        if (gameMode.equals("SinglePlayer")) {
            activeBoard.setMyPieceColor(1);
        }

        if (gameMode.equals("MultiPlayer")) {
            Message waitMsg = new Message();
            waitMsg.type = Message.WAITING;
            waitMsg.sender = myUsername;
            clientConnection.send(waitMsg);
            activeBoard.showWaiting(1);
        }
    }

    private void handleIncoming(Message msg) {
        switch (msg.type) {
            case Message.SIGN_IN_OK: {
                myUsername = msg.sender;
                primaryStage.setTitle("Checkers - " + myUsername);
                greeting.setText(selectedLanguage.equals("Spanish") ? "Bienvenido, " + myUsername + "!" : "Welcome, " + myUsername + "!");
                primaryStage.setScene(sceneMap.get("mainScene"));
                break;
            }
            case Message.SIGN_IN_FAIL: {
                errorLabel.setText(selectedLanguage.equals("Spanish") ? "Nombre de usuario no disponible." : "Username taken. Try another.");
                errorLabel.setVisible(true);
                break;
            }
            case Message.GAME_START: {
                if (activeBoard != null) {
                    opponentUsername = msg.recipient;
                    myPieceColor = msg.content.equals("LIGHT") ? 1 : 2;
                    activeBoard.setPlayerNames(myUsername, opponentUsername);
                    activeBoard.setMyPieceColor(myPieceColor);
                    activeBoard.hideOverlay();
                    activeBoard.resetBoard();
                }
                break;
            }
            case Message.GAME_MOVE: {
                if (activeBoard != null) {
                    activeBoard.applyOpponentMove(msg.fromRow, msg.fromCol, msg.toRow, msg.toCol);
                }
                break;
            }
            case Message.GAME_OVER: {
                if (activeBoard != null) {
                    boolean won = "WIN".equals(msg.content);
                    boolean opponentLeft = won && opponentUsername == null;
                    opponentUsername = null;
                    if (won) sessionWins++; else sessionLosses++;
                    activeBoard.setRecord(sessionWins, sessionLosses);
                    activeBoard.showGameOver(won, opponentLeft);
                }
                break;
            }
            case Message.OPPONENT_FORFEIT: {
                opponentUsername = null;
                if (activeBoard != null) {
                    activeBoard.disableRematch();
                }
                break;
            }
            case Message.WAITING: {
                if (activeBoard != null) {
                    activeBoard.showWaiting(msg.connectedCount);
                }
                break;
            }
            case Message.CHAT_MESSAGE: {
                if (activeBoard != null) {
                    activeBoard.appendMessage(msg.content);
                }
                else {
                    chatList.getItems().add(msg.content);
                }
                break;
            }
            case Message.USER_LIST: {
                if (userCombo != null) {
                    userCombo.getItems().clear();
                    userCombo.getItems().addAll(msg.userList);
                }
                break;
            }
            case Message.GROUP_LIST: {
                if (groupCombo != null) {
                    groupCombo.getItems().clear();
                    groupCombo.getItems().addAll(msg.groupList);
                }
                break;
            }
            default: {
                break;
            }
        }
    }

    private void showChatPopup() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);

        targetGroup = new ToggleGroup();
        rbAll = new RadioButton(selectedLanguage.equals("Spanish") ? "Todos" : "All");
        rbAll.setToggleGroup(targetGroup); rbAll.setSelected(true);
        rbPrivate = new RadioButton(selectedLanguage.equals("Spanish") ? "Usuario" : "User");
        rbPrivate.setToggleGroup(targetGroup);
        rbGroup = new RadioButton(selectedLanguage.equals("Spanish") ? "Grupo" : "Group");
        rbGroup.setToggleGroup(targetGroup);

        rbAll.getStyleClass().add("chat-radio");
        rbPrivate.getStyleClass().add("chat-radio");
        rbGroup.getStyleClass().add("chat-radio");

        messageField.setPromptText(selectedLanguage.equals("Spanish") ? "Escribe un mensaje..." : "Type a message...");
        messageField.getStyleClass().add("chat-field");

        userCombo.setPromptText(selectedLanguage.equals("Spanish") ? "Seleccionar usuario" : "select user");
        userCombo.disableProperty().bind(rbPrivate.selectedProperty().not());
        groupCombo.setPromptText(selectedLanguage.equals("Spanish") ? "Grupo selecto" : "select group");
        groupCombo.disableProperty().bind(rbGroup.selectedProperty().not());

        groupNameField = new TextField();
        groupNameField.setPromptText(selectedLanguage.equals("Spanish") ? "Nuevo nombre de grupo" : "New group name");
        groupNameField.getStyleClass().add("chat-field");

        Button createGroupBtn = new Button(selectedLanguage.equals("Spanish") ? "Crear" : "Create");
        createGroupBtn.getStyleClass().add("btn-dark-small");
        createGroupBtn.setOnAction(e -> {
            String g = groupNameField.getText().trim();
            if (g.isEmpty()) return;
            groupNameField.clear();
            clientConnection.send(Message.createGroup(myUsername, g));
        });

        Button joinGroupBtn = new Button(selectedLanguage.equals("Spanish") ? "Unirse" : "Join");
        joinGroupBtn.getStyleClass().add("btn-dark-small");
        joinGroupBtn.setOnAction(e -> {
            String g = groupCombo.getValue();
            if (g == null) { chatList.getItems().add("[Error] Select a group to join."); return; }
            clientConnection.send(Message.joinGroup(myUsername, g));
        });

        sendBtn.getStyleClass().setAll("button", "btn-red-small");

        HBox targetRow = new HBox(8, rbAll, rbPrivate, userCombo, rbGroup, groupCombo);
        targetRow.setPadding(new Insets(4, 8, 4, 8));
        targetRow.setAlignment(Pos.CENTER_LEFT);

        HBox groupRow = new HBox(8, groupNameField, createGroupBtn, joinGroupBtn);
        groupRow.setPadding(new Insets(0, 8, 0, 8));

        HBox sendRow = new HBox(8, messageField, sendBtn);
        sendRow.setPadding(new Insets(0, 8, 8, 8));
        HBox.setHgrow(messageField, Priority.ALWAYS);

        chatList.getStyleClass().add("chat-list");
        VBox root = new VBox(6, chatList, targetRow, groupRow, sendRow);
        root.getStyleClass().add("chat-root");
        VBox.setVgrow(chatList, Priority.ALWAYS);

        Scene scene = new Scene(root, 600, 500);
        scene.getStylesheets().add(getClass().getResource("/assets/checkers.css").toExternalForm());
        popup.setScene(scene);
        popup.setTitle("Chat - " + myUsername);
        popup.show();
    }

    public void showLanguagePopup() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Language");

        Button english = new Button("English");
        Button spanish = new Button("Spanish");

        if (selectedLanguage.equals("English")) {
            english.getStyleClass().setAll("button", "btn-green");
            spanish.getStyleClass().setAll("button", "btn-unselected");
        }
        else {
            spanish.getStyleClass().setAll("button", "btn-green");
            english.getStyleClass().setAll("button", "btn-unselected");
        }

        english.setOnAction(e -> {
            selectedLanguage = "English";
            english.getStyleClass().setAll("button", "btn-green");
            spanish.getStyleClass().setAll("button", "btn-unselected");
            applyLanguage();
        });
        spanish.setOnAction(e -> {
            selectedLanguage = "Spanish";
            spanish.getStyleClass().setAll("button", "btn-green");
            english.getStyleClass().setAll("button", "btn-unselected");
            applyLanguage();
        });

        VBox layout = new VBox(10, english, spanish);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.getStyleClass().add("main-root");

        Scene scene = new Scene(layout, 350, 220);
        scene.getStylesheets().add(getClass().getResource("/assets/checkers.css").toExternalForm());
        popup.setScene(scene);
        popup.showAndWait();
    }

    private void applyLanguage() {
        boolean sp = selectedLanguage.equals("Spanish");
        singlePlayer.setText(sp ? "Un Jugador" : "Single Player");
        multiPlayer.setText(sp ? "Multijugador" : "Multiplayer");
        language.setText(sp ? "Idioma" : "Language");
        messaging.setText(sp ? "mensajería" : "Message");
        sendBtn.setText(sp ? "Enviar" : "Send");
        if (myUsername != null) {
            greeting.setText(sp ? "Bienvenido, " + myUsername + "!" : "Welcome, " + myUsername + "!");
        }

        signInBtn.setText(sp ? "Iniciar Sesión" : "Sign In");
        usernameField.setPromptText(sp ? "Ingresa tu nombre" : "Enter username");

        if (!errorLabel.getText().isEmpty()) {
            errorLabel.setText(sp ? "Nombre de usuario no disponible." : "Username taken. Try another.");
        }

        if (activeBoard != null) activeBoard.applyLanguage(selectedLanguage);
    }

    private void attemptSignIn() {
        String name = usernameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText(selectedLanguage.equals("Spanish") ? "El nombre no puede estar vacío." : "Username cannot be empty.");
            errorLabel.setVisible(true);
            return;
        }
        errorLabel.setVisible(false);

        if (clientConnection != null && clientConnection.isAlive()) {
            clientConnection.send(Message.signIn(name));
        } else {
            clientConnection = new Client(
                    data -> {
                        if (!(data instanceof Message)) return;
                        Platform.runLater(() -> handleIncoming((Message) data));
                    },
                    () -> clientConnection.send(Message.signIn(name))
            );
            clientConnection.start();
        }
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        messageField.clear();

        RadioButton sel = (RadioButton) targetGroup.getSelectedToggle();
        if (sel == rbAll) {
            clientConnection.send(Message.sendAll(myUsername, text));
        }
        else if (sel == rbPrivate) {
            String target = userCombo.getValue();
            if (target == null) {
                chatList.getItems().add("[Error] Select a user first.");
                return;
            }
            clientConnection.send(Message.sendPrivate(myUsername, target, text));
        }
        else if (sel == rbGroup) {
            String g = groupCombo.getValue();
            if (g == null) {
                chatList.getItems().add("[Error] Select a group first.");
                return;
            }
            clientConnection.send(Message.sendGroup(myUsername, g, text));
        }
    }

    public Scene createMainScreenGui() {
        HBox centered = new HBox(playButtonVBox);
        centered.setAlignment(Pos.CENTER);
        centered.setPadding(new Insets(100, 0, 80, 0));

        mainVBox.setAlignment(Pos.CENTER);
        mainVBox.getChildren().setAll(greeting, checkersIcon);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-root");
        root.setCenter(mainVBox);
        root.setBottom(centered);

        Scene scene = new Scene(root, 1000, 600);
        Font.loadFont(getClass().getResourceAsStream("/assets/Silkscreen/Silkscreen-Regular.ttf"), 12);
        Font.loadFont(getClass().getResourceAsStream("/assets/Silkscreen/Silkscreen-Bold.ttf"), 12);
        scene.getStylesheets().add(getClass().getResource("/assets/checkers.css").toExternalForm());
        return scene;
    }

    public Scene createLoginGui() {
        VBox box = new VBox(10, new Label("Username:"), usernameField, errorLabel, signInBtn);
        box.getStyleClass().add("login-root");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Scene scene = new Scene(box, 600, 400);
        scene.getStylesheets().add(getClass().getResource("/assets/checkers.css").toExternalForm());
        return scene;
    }
}