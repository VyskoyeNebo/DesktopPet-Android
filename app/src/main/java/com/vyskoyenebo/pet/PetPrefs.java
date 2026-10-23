package com.vyskoyenebo.pet;

import android.content.Context;
import android.content.SharedPreferences;

final class PetPrefs {
    private final SharedPreferences data;
    PetPrefs(Context context) { data = context.getSharedPreferences("pet", Context.MODE_PRIVATE); }
    String form() { String s = data.getString("form", "default"); return Forms.valid(s) ? s : "default"; }
    void form(String id) { data.edit().putString("form", id).apply(); }
    String motion(String id) { return data.getString("motion_" + id, ""); }
    void motion(String id, String motion) { data.edit().putString("motion_" + id, motion).apply(); }
    int size(String id) { return Math.max(64, Math.min(512, data.getInt("size_" + id, Forms.defaultSize(id)))); }
    void size(String id, int size) { data.edit().putInt("size_" + id, size).apply(); }
    boolean fixed() { return data.getBoolean("fixed", false); }
    void fixed(boolean v) { data.edit().putBoolean("fixed", v).apply(); }
    boolean through() { return data.getBoolean("through", false); }
    void through(boolean v) { data.edit().putBoolean("through", v).apply(); }
    float x() { return data.getFloat("x", 0.9f); }
    float y() { return data.getFloat("y", 0.8f); }
    void position(float x, float y) { data.edit().putFloat("x", x).putFloat("y", y).apply(); }
}
