module com.mahjong_java.mahjong_java {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.google.gson;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires jpro.webapi;

    opens com.mahjong_java.mahjong_java to javafx.fxml, com.google.gson,jpro.webapi;
    exports com.mahjong_java.mahjong_java;
}