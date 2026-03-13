module sn.messagerie.app_messagerie {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;
    requires jakarta.persistence;
    requires bcrypt;
    requires org.slf4j;
    requires java.desktop;

    requires org.hibernate.orm.core;

    requires java.sql;

    opens sn.messagerie.app_messagerie to javafx.fxml;
    opens sn.messagerie.app_messagerie.client.fx to javafx.fxml;
    opens sn.messagerie.app_messagerie.model to org.hibernate.orm.core;
    exports sn.messagerie.app_messagerie;
}
