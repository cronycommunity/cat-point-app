module com.udacity.catpoint.securityservice.service {
        requires transitive java.desktop;
        requires transitive miglayout.swing;
        requires transitive java.prefs;
        requires transitive gson;
        requires transitive com.google.common;
        requires transitive com.udacity.catpoint.imageservice;
        requires transitive java.sql;
        opens com.udacity.catpoint.securityservice.data to gson;
        opens com.udacity.catpoint.securityservice.service;
        }