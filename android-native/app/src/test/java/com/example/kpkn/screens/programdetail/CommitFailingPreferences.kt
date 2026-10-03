package com.example.kpkn.screens.programdetail

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * Contexto cuyo SharedPreferences "se llena el disco": todo `commit()` devuelve false. Sirve para
 * probar que «Reemplazar todo» no cierra la app ni toca el programa cuando no se puede guardar
 * la copia recuperable previa (D2.3).
 */
internal class CommitFailingContext(base: Context) : ContextWrapper(base) {
    val preferences = CommitFailingPreferences()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
}

internal class CommitFailingPreferences : SharedPreferences {
    var commitAttempts = 0
        private set

    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()
    override fun getString(key: String?, defValue: String?): String? = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = false
    override fun edit(): SharedPreferences.Editor = FailingEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FailingEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
        override fun commit(): Boolean {
            commitAttempts++
            return false
        }
        override fun apply() = Unit
    }
}
