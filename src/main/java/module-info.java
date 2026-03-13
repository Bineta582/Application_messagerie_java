module sn.messagerie.app_messagerie {
    requires javafx.controls;
    requires javafx.fxml;
    requires jakarta.persistence;
    requires bcrypt;
    requires org.slf4j;

    opens sn.messagerie.app_messagerie to javafx.fxml;
    exports sn.messagerie.app_messagerie;
}
