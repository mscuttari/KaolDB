package it.mscuttari.kaoldb.core;

import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import it.mscuttari.kaoldb.exceptions.InvalidConfigException;
import it.mscuttari.kaoldb.interfaces.DatabaseSchemaMigrator;

class Config {

    /** Maps each database name to its {@link DatabaseObject}*/
    public Map<String, DatabaseObject> mapping;


    /** Whether the debug messages should be enabled or not */
    public boolean debug;


    /**
     * Constructor
     */
    public Config() {
        mapping = new HashMap<>();
        debug = false;
    }


    /**
     * Parse the XML configuration file
     *
     * @param   xml     the XmlResourceParser instance used to read the configuration file
     *
     * @throws  XmlPullParserException      in case of parsing error
     * @throws  IOException                 in case of general i/o error
     */
    public void parseConfigFile(XmlResourceParser xml) throws XmlPullParserException, IOException {
        int eventType = xml.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && xml.getName().equals("databases")) {
                parseDatabasesList(xml);
            }

            eventType = xml.next();
        }
    }


    /**
     * Iterate through databases list
     *
     * @param   xml     the XmlResourceParser instance used to read the configuration file
     *
     * @throws  XmlPullParserException      in case of parsing error
     * @throws  IOException                 in case of general i/o error
     */
    private void parseDatabasesList(XmlResourceParser xml) throws XmlPullParserException, IOException {
        int eventType = xml.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && xml.getName().equals("database")) {
                parseDatabaseSection(xml);
            }

            eventType = xml.next();
        }
    }


    /**
     * Parse single database section
     *
     * @param   xml     the XmlResourceParser instance used to read the configuration file
     *
     * @throws  XmlPullParserException      in case of parsing error
     * @throws  IOException                 in case of general i/o error
     */
    private void parseDatabaseSection(XmlResourceParser xml) throws XmlPullParserException, IOException {
        LogUtils.v("Parsing database section");
        DatabaseObject database = new DatabaseObject();

        // Name
        database.name = xml.getAttributeValue(null, "name");

        if (database.name == null || database.name.isEmpty()) {
            throw new InvalidConfigException("Database name not specified");
        }

        // Version
        String version = xml.getAttributeValue(null, "version");

        if (version == null || version.isEmpty())
            throw new InvalidConfigException("Database " + database.name + ": version not set");

        try {
            database.version = Integer.valueOf(version);
        } catch (NumberFormatException e) {
            throw new InvalidConfigException("Database " + database.name + ": invalid version");
        }

        // Schema migrator
        String migrator = xml.getAttributeValue(null, "migrator");

        if (migrator != null && !migrator.isEmpty()) {
            try {
                Class<?> migratorClass = Class.forName(migrator);
                if (DatabaseSchemaMigrator.class.isAssignableFrom(migratorClass)) {
                    database.migrator = migratorClass.asSubclass(DatabaseSchemaMigrator.class);
                } else {
                    throw new InvalidConfigException("Database " + database.name + ": invalid schema migrator");
                }

            } catch (ClassNotFoundException e) {
                throw new InvalidConfigException("Database " + database.name + ": invalid schema migrator");
            }
        }


        LogUtils.i("Database found: [" +
                "name = " + database.name + ", " +
                "version = " + database.version + ", " +
                "migrator = " + database.migrator.getSimpleName() + "]"
        );


        // Classes
        database.classes = new ArrayList<>();

        int eventType = xml.getEventType();

        while (eventType != XmlPullParser.END_TAG || xml.getName().equals("class")) {
            if (eventType == XmlPullParser.START_TAG && xml.getName().equals("class")) {
                //noinspection UnusedAssignment
                eventType = xml.next();

                try {
                    Class<?> clazz = Class.forName(xml.getText());
                    database.classes.add(clazz);
                    LogUtils.i("Database " + database.name + ": found class " + clazz.getSimpleName());

                } catch (ClassNotFoundException e) {
                    throw new InvalidConfigException("Database " + database.name + ": class " + xml.getText() + " not found");
                }
            }

            eventType = xml.next();
        }

        LogUtils.e("Database " + database.name + ": classes mapped");

        // Add database to databases list
        mapping.put(database.name, database);
    }

}