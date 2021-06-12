module com.udacity.catpoint.securityservice.service {
        requires transitive java.desktop;
        requires transitive java.prefs;
        requires transitive gson;
        requires transitive com.google.common;
        requires transitive com.udacity.catpoint.imageservice;
        requires transitive java.sql;
        requires miglayout;
        exports com.udacity.catpoint.securityservice.service;
        exports com.udacity.catpoint.securityservice.application;
        exports com.udacity.catpoint.securityservice.data;
}