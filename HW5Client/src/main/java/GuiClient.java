import java.util.HashMap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
    Stage primaryStage;

    ListView<String> chatList;

    ToggleGroup targetGroup;
    RadioButton rbAll, rbPrivate, rbGroup;

    ComboBox<String> userCombo = new ComboBox<>();
    ComboBox<String> groupCombo = new ComboBox<>();

    TextField groupNameField;

    Label errorLabel, greeting;
    ImageView checkersIcon;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        clientConnection = new Client(data -> {
            if (!(data instanceof Message))
                return;
            Message msg = (Message) data;
            Platform.runLater(() -> handleIncoming(msg));
        });
        clientConnection.start();

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

        sceneMap = new HashMap<>();
        sceneMap.put("mainScene", createMainScreenGui());
        sceneMap.put("signIn", createLoginGui());

        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
        primaryStage.setScene(sceneMap.get("signIn"));
        primaryStage.setTitle("Checkers");
        primaryStage.show();
    }

    private void showChatPopup() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);

        targetGroup = new ToggleGroup();
        rbAll = new RadioButton("All");
        rbAll.setToggleGroup(targetGroup);
        rbAll.setSelected(true);
        rbPrivate = new RadioButton("User");
        rbPrivate.setToggleGroup(targetGroup);
        rbGroup = new RadioButton("Group");
        rbGroup.setToggleGroup(targetGroup);

        rbAll.getStyleClass().add("chat-radio");
        rbPrivate.getStyleClass().add("chat-radio");
        rbGroup.getStyleClass().add("chat-radio");

        userCombo.setPromptText("select user");
        userCombo.disableProperty().bind(rbPrivate.selectedProperty().not());
        groupCombo.setPromptText("select group");
        groupCombo.disableProperty().bind(rbGroup.selectedProperty().not());

        groupNameField = new TextField();
        groupNameField.setPromptText("New group name");
        groupNameField.getStyleClass().add("chat-field");

        messageField.getStyleClass().add("chat-field");

        Button createGroupBtn = new Button("Create");
        createGroupBtn.getStyleClass().add("btn-dark-small");
        createGroupBtn.setOnAction(e -> {
            String gName = groupNameField.getText().trim();
            if (gName.isEmpty()) return;
            groupNameField.clear();
            clientConnection.send(Message.createGroup(myUsername, gName));
        });

        Button joinGroupBtn = new Button("Join");
        joinGroupBtn.getStyleClass().add("btn-dark-small");
        joinGroupBtn.setOnAction(e -> {
            String gName = groupCombo.getValue();
            if (gName == null) {
                chatList.getItems().add("[Error] Select a group to join.");
                return;
            }
            clientConnection.send(Message.joinGroup(myUsername, gName));
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

    private void showLanguagePopup() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Language");

        Button english = new Button("English");
        Button spanish = new Button("Spanish");

        if (selectedLanguage.equals("English")) {
            english.getStyleClass().setAll("button", "btn-green");
            spanish.getStyleClass().setAll("button", "btn-unselected");
        } else {
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
        if (selectedLanguage.equals("Spanish")) {
            singlePlayer.setText("Un Jugador");
            multiPlayer.setText("Multijugador");
            language.setText("Idioma");
            messaging.setText("mensajería");
            if (myUsername != null)
                greeting.setText("Bienvenido, " + myUsername + "!");
            signInBtn.setText("Iniciar Sesión");
            usernameField.setPromptText("Ingresa tu nombre");
            if (!errorLabel.getText().isEmpty())
                errorLabel.setText("Nombre de usuario no disponible.");
        } else {
            singlePlayer.setText("Single Player");
            multiPlayer.setText("Multiplayer");
            language.setText("Language");
            messaging.setText("Message");
            if (myUsername != null)
                greeting.setText("Welcome, " + myUsername + "!");
            signInBtn.setText("Sign In");
            usernameField.setPromptText("Enter username");
            if (!errorLabel.getText().isEmpty())
                errorLabel.setText("Username taken. Try another.");
        }
    }

    private void handleIncoming(Message msg) {
        switch (msg.type) {
            case Message.SIGN_IN_OK:
                myUsername = msg.sender;
                primaryStage.setTitle("Checkers - " + myUsername);
                greeting.setText(selectedLanguage.equals("Spanish") ? "Bienvenido, " + myUsername + "!" : "Welcome, " + myUsername + "!");
                primaryStage.setScene(sceneMap.get("mainScene"));
                primaryStage.setMaximized(true);
                break;
            case Message.SIGN_IN_FAIL:
                errorLabel.setText(selectedLanguage.equals("Spanish") ? "Nombre de usuario no disponible." : "Username taken. Try another.");
                errorLabel.setVisible(true);
                break;
            case Message.USER_LIST:
                String prevUser = userCombo.getValue();
                userCombo.setItems(FXCollections.observableArrayList(msg.userList));
                if (prevUser != null && msg.userList.contains(prevUser))
                    userCombo.setValue(prevUser);
                break;
            case Message.GROUP_LIST:
                String prevGroup = groupCombo.getValue();
                groupCombo.setItems(FXCollections.observableArrayList(msg.groupList));
                if (prevGroup != null && msg.groupList.contains(prevGroup))
                    groupCombo.setValue(prevGroup);
                break;
            case Message.CHAT_MESSAGE:
            default:
                chatList.getItems().add(msg.content != null ? msg.content : msg.toString());
                chatList.scrollTo(chatList.getItems().size() - 1);
                break;
        }
    }

    private void attemptSignIn() {
        String name = usernameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText(selectedLanguage.equals("Spanish") ? "El nombre no puede estar vacío." : "Username cannot be empty.");
            errorLabel.setVisible(true);
            return;
        }
        errorLabel.setVisible(false);
        clientConnection.send(Message.signIn(name));
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty())
            return;
        messageField.clear();

        RadioButton selected = (RadioButton) targetGroup.getSelectedToggle();
        if (selected == rbAll) {
            clientConnection.send(Message.sendAll(myUsername, text));
        } else if (selected == rbPrivate) {
            String target = userCombo.getValue();
            if (target == null) {
                chatList.getItems().add("[Error] Select a user first.");
                return;
            }
            clientConnection.send(Message.sendPrivate(myUsername, target, text));
        } else if (selected == rbGroup) {
            String gName = groupCombo.getValue();
            if (gName == null) {
                chatList.getItems().add("[Error] Select a group first.");
                return;
            }
            clientConnection.send(Message.sendGroup(myUsername, gName, text));
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

        Scene scene = new Scene(root, 1200, 800);
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