package com.example.quickshifter_controller;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SQL_DataBase {
    private static final String DataBaseName = "My_DataBase";
    private static final int DataBaseVersion = 1;
    private static final String DataBaseTable = "User";

    private static SQLiteDatabase database;
    private static SQL_DataBaseHelper sqlDataBaseHelper;

    private Context context;

    public SQL_DataBase(Context context) {
        this.context = context;
        sqlDataBaseHelper = new SQL_DataBaseHelper(context, DataBaseName, null, DataBaseVersion, DataBaseTable);
        database = sqlDataBaseHelper.getWritableDatabase();
    }

    public void Insert_data(String name, String data) {
        ContentValues Values = new ContentValues();
        Values.put("Name", name);
        Values.put("Data", data);
        long state = database.insert(DataBaseTable, null, Values);
        System.out.println(state);
    }

    public void Update_data(String Name, String data) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("Data", data);
        if(Select_data(Name)==null)
            Insert_data(Name,data);
        try {
            database.update(DataBaseTable, contentValues, "Name='" + Name + "'", null);
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    public void Delete_data(String Name) {
        database.delete(DataBaseTable, "Name='" + Name + "'", null);
    }

    public String Select_data(String name) {
        Cursor cursor = database.rawQuery("SELECT * FROM " + DataBaseTable, null);

        List<String[]> data_list = new ArrayList<>();

        if (!cursor.moveToFirst())
            return null;
        for (int i = 0; i < cursor.getCount(); i++) {
            int Name_index = cursor.getColumnIndex("Name");
            int Data_index = cursor.getColumnIndex("Data");
            data_list.add(new String[]{cursor.getString(Name_index), cursor.getString(Data_index)});
            cursor.moveToNext();
        }
        cursor.close();
        String data = "";
        for (String[] data_list_array : data_list) {
            if (Objects.equals(data_list_array[0], name)) {
                data = data_list_array[1];
                break;
            }
        }
        return data;
    }
}

class SQL_DataBaseHelper extends SQLiteOpenHelper {
    String SqlTable = null;

    public SQL_DataBaseHelper(Context context, String Name, SQLiteDatabase.CursorFactory factory, int version, String TableName) {
        super(context, Name, null, version);

        SqlTable = "CREATE TABLE IF NOT EXISTS "
                + TableName +
                " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "Name TEXT not null," +
                "Data TEXT not null" +
                ")";
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        if (SqlTable == null)
            return;
        sqLiteDatabase.execSQL(SqlTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        final String SQL = "DROP TABLE Users";
        sqLiteDatabase.execSQL(SQL);
    }
}
