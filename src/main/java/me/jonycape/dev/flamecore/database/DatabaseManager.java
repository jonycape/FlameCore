package me.jonycape.dev.flamecore.database;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;

@RequiredArgsConstructor
public final class DatabaseManager {

    private final Main plugin;
    private File dataFile;
    private Connection connection;

    public void init() {
        String dbName = Main.getCfg().getString(ConfigKeys.DATABASE_FILE, "flamecore");
        this.dataFile = new File(plugin.getDataFolder(), dbName);
    }

    public Connection getConnection() {
        if (connection == null) {
            connect();
        }
        return connection;
    }

    private void connect() {
        try {
            Class.forName("org.h2.Driver");
            this.connection = DriverManager.getConnection(
                    "jdbc:h2:" + dataFile.getAbsolutePath(), "sa", "");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось подключиться к базе данных H2", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка при закрытии соединения с базой", e);
            }
            connection = null;
        }
    }
}