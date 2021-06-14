module com.udacity.catpoint.imageservice {
    requires java.desktop;
    requires transitive software.amazon.awssdk.auth;
    requires transitive software.amazon.awssdk.core;
    requires transitive software.amazon.awssdk.regions;
    requires transitive software.amazon.awssdk.services.rekognition;
    requires org.slf4j;
    exports com.udacity.catpoint.imageservice.service;
}