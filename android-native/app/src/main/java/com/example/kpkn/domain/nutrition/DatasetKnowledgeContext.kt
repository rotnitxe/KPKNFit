package com.example.kpkn.domain.nutrition

object DatasetKnowledgeContext {
    data class ContextProfile(val count: Int, val typicalGrams: List<Double>, val typicalKcal: List<Double>, val typicalProtein: List<Double>, val typicalFats: List<Double>, val typicalCarbs: List<Double>)
    data class MacroRange(val count: Int, val kcalMin: Double, val kcalMax: Double, val kcalMedian: Double, val proteinMin: Double, val proteinMax: Double, val proteinMedian: Double, val fatsMin: Double, val fatsMax: Double, val fatsMedian: Double, val carbsMin: Double, val carbsMax: Double, val carbsMedian: Double)

    val CONTEXT_KEYWORDS: Map<String, List<String>> = mapOf(
        "CASINO" to listOf("casino", "cafeteria", "comedor", "buffet del", "menu del dia"),
        "POST_ENTRENO" to listOf("post-entreno", "post entreno", "post-entrenamiento", "recuperacion", "post-sentadillas", "post-pecho", "post-espalda", "post-pierna"),
        "POWERBUILDER" to listOf("powerbuilder", "power builder", "volumen extremo", "masa extrema"),
        "ABUELA_CHILENA" to listOf("abuela chilena", "abuela", "contundente", "plato hondo", "tazon grande", "plato rebosante"),
        "OFICINA" to listOf("oficina", "escritorio", "trabajo", "reunion", "break de oficina"),
        "ESTUDIANTE" to listOf("estudiante", "universidad", "facultad", "campus", "barato", "corto de lucas", "sobrevivencia"),
        "CONDOMINIO" to listOf("condominio", "edificio", "departamento", "casa"),
        "SNACK" to listOf("snack", "colacion", "merendola", "merienda", "tentempie", "piscolabis", "refrigerio"),
        "DESAYUNO" to listOf("desayuno", "desayunar", "manana"),
        "ALMUERZO" to listOf("almuerzo", "almorzar", "mediodia", "tarde"),
        "CENA" to listOf("cena", "cenar", "noche", "nocturno", "antes de dormir"),
    )

    val INTENSIFIER_KEYWORDS: Map<String, List<String>> = mapOf(
        "GIGANTE" to listOf("gigante", "gigantesca", "gigantesco", "enorme", "descomunal", "bestial"),
        "GENEROSO" to listOf("generoso", "generosa", "generosos", "generosas"),
        "COLMADO" to listOf("colmado", "colmada", "colmados", "colmadas", "hasta el borde", "rebosante", "rebosantes", "lleno", "llena", "repleto"),
        "GRANDE" to listOf("grande", "grandes", "gran"),
        "PEQUENO" to listOf("pequeno", "pequena", "pequenas", "pequenos", "chico", "chica", "chicos", "chicas"),
        "FINO" to listOf("fino", "fina", "finos", "finas", "delgado", "delgada", "delgados", "delgadas"),
        "GRUESO" to listOf("grueso", "gruesa", "gruesos", "gruesas", "gordo", "gorda"),
    )

    val CONTEXT_PROFILES: Map<String, ContextProfile> = mapOf(
        "CASINO" to ContextProfile(510, listOf(180.0, 200.0, 250.0, 300.0, 150.0, 100.0, 80.0, 120.0, 60.0, 90.0), listOf(350.0, 420.0, 280.0, 310.0, 380.0, 450.0, 320.0, 290.0, 360.0, 400.0), listOf(15.0, 18.0, 12.0, 20.0, 22.0, 16.0, 14.0, 19.0, 17.0, 21.0), listOf(12.0, 15.0, 10.0, 18.0, 20.0, 14.0, 11.0, 16.0, 13.0, 17.0), listOf(45.0, 55.0, 35.0, 40.0, 50.0, 60.0, 42.0, 38.0, 48.0, 52.0)),
        "POST_ENTRENO" to ContextProfile(116, listOf(200.0, 250.0, 300.0, 180.0, 220.0, 280.0, 150.0, 190.0, 240.0, 260.0), listOf(500.0, 600.0, 700.0, 450.0, 550.0, 650.0, 480.0, 520.0, 580.0, 620.0), listOf(40.0, 50.0, 60.0, 35.0, 45.0, 55.0, 38.0, 42.0, 48.0, 52.0), listOf(10.0, 15.0, 20.0, 8.0, 12.0, 18.0, 9.0, 11.0, 14.0, 16.0), listOf(50.0, 60.0, 70.0, 45.0, 55.0, 65.0, 48.0, 52.0, 58.0, 62.0)),
        "POWERBUILDER" to ContextProfile(46, listOf(300.0, 350.0, 400.0, 280.0, 320.0, 380.0, 250.0, 290.0, 340.0, 360.0), listOf(800.0, 900.0, 1000.0, 750.0, 850.0, 950.0, 780.0, 820.0, 880.0, 920.0), listOf(60.0, 70.0, 80.0, 55.0, 65.0, 75.0, 58.0, 62.0, 68.0, 72.0), listOf(25.0, 30.0, 35.0, 22.0, 28.0, 33.0, 23.0, 26.0, 29.0, 31.0), listOf(80.0, 90.0, 100.0, 75.0, 85.0, 95.0, 78.0, 82.0, 88.0, 92.0)),
        "SNACK" to ContextProfile(200, listOf(30.0, 40.0, 50.0, 25.0, 35.0, 45.0, 20.0, 28.0, 38.0, 42.0), listOf(150.0, 200.0, 250.0, 120.0, 180.0, 220.0, 130.0, 160.0, 190.0, 210.0), listOf(8.0, 10.0, 12.0, 6.0, 9.0, 11.0, 7.0, 8.0, 10.0, 11.0), listOf(5.0, 8.0, 10.0, 4.0, 7.0, 9.0, 5.0, 6.0, 8.0, 9.0), listOf(15.0, 20.0, 25.0, 12.0, 18.0, 22.0, 14.0, 16.0, 19.0, 21.0)),
        "DESAYUNO" to ContextProfile(150, listOf(200.0, 250.0, 300.0, 180.0, 220.0, 280.0, 150.0, 190.0, 240.0, 260.0), listOf(350.0, 400.0, 450.0, 300.0, 380.0, 420.0, 320.0, 360.0, 390.0, 410.0), listOf(15.0, 18.0, 22.0, 12.0, 16.0, 20.0, 13.0, 15.0, 17.0, 19.0), listOf(10.0, 12.0, 15.0, 8.0, 11.0, 14.0, 9.0, 10.0, 12.0, 13.0), listOf(40.0, 50.0, 60.0, 35.0, 45.0, 55.0, 38.0, 42.0, 48.0, 52.0)),
        "ALMUERZO" to ContextProfile(200, listOf(250.0, 300.0, 350.0, 220.0, 280.0, 320.0, 200.0, 240.0, 290.0, 310.0), listOf(500.0, 600.0, 700.0, 450.0, 550.0, 650.0, 480.0, 520.0, 580.0, 620.0), listOf(30.0, 35.0, 40.0, 25.0, 32.0, 38.0, 28.0, 30.0, 34.0, 36.0), listOf(15.0, 20.0, 25.0, 12.0, 18.0, 22.0, 14.0, 16.0, 19.0, 21.0), listOf(50.0, 60.0, 70.0, 45.0, 55.0, 65.0, 48.0, 52.0, 58.0, 62.0)),
        "CENA" to ContextProfile(120, listOf(200.0, 250.0, 300.0, 180.0, 220.0, 280.0, 150.0, 190.0, 240.0, 260.0), listOf(400.0, 450.0, 500.0, 350.0, 420.0, 480.0, 370.0, 400.0, 440.0, 460.0), listOf(25.0, 30.0, 35.0, 20.0, 28.0, 32.0, 22.0, 25.0, 29.0, 31.0), listOf(12.0, 15.0, 18.0, 10.0, 14.0, 16.0, 11.0, 12.0, 15.0, 16.0), listOf(40.0, 50.0, 60.0, 35.0, 45.0, 55.0, 38.0, 42.0, 48.0, 52.0)),
        "OFICINA" to ContextProfile(9, listOf(30.0, 40.0, 50.0, 25.0, 35.0, 45.0, 20.0, 28.0, 38.0), listOf(150.0, 200.0, 250.0, 120.0, 180.0, 220.0, 130.0, 160.0, 190.0), listOf(5.0, 8.0, 10.0, 4.0, 7.0, 9.0, 5.0, 6.0, 8.0), listOf(5.0, 8.0, 10.0, 4.0, 7.0, 9.0, 5.0, 6.0, 8.0), listOf(15.0, 20.0, 25.0, 12.0, 18.0, 22.0, 14.0, 16.0, 19.0)),
        "ESTUDIANTE" to ContextProfile(4, listOf(200.0, 250.0, 300.0, 180.0), listOf(300.0, 400.0, 500.0, 280.0), listOf(12.0, 15.0, 20.0, 10.0), listOf(8.0, 12.0, 15.0, 7.0), listOf(30.0, 40.0, 50.0, 28.0)),
        "ABUELA_CHILENA" to ContextProfile(11, listOf(300.0, 350.0, 400.0, 280.0, 320.0, 380.0, 250.0, 290.0, 340.0, 360.0), listOf(600.0, 700.0, 800.0, 550.0, 650.0, 750.0, 580.0, 620.0, 680.0, 720.0), listOf(30.0, 35.0, 40.0, 25.0, 32.0, 38.0, 28.0, 30.0, 34.0, 36.0), listOf(20.0, 25.0, 30.0, 18.0, 22.0, 28.0, 19.0, 21.0, 24.0, 26.0), listOf(60.0, 70.0, 80.0, 55.0, 65.0, 75.0, 58.0, 62.0, 68.0, 72.0)),
        "CONDOMINIO" to ContextProfile(1, listOf(250.0), listOf(400.0), listOf(15.0), listOf(12.0), listOf(40.0)),
    )

    val MACRO_RANGES: Map<String, MacroRange> = mapOf(
        "MACRO_CALC" to MacroRange(3737, 50.0, 1600.0, 465.0, 3.0, 92.0, 46.0, 0.0, 110.0, 12.0, 1.0, 190.0, 55.0),
        "DATABASE_LOOKUP" to MacroRange(3747, 10.0, 500.0, 130.0, 0.5, 35.0, 13.0, 0.0, 45.0, 2.0, 0.0, 80.0, 15.0),
        "GENERAL" to MacroRange(8722, 1.0, 1600.0, 350.0, 0.0, 92.0, 30.0, 0.0, 110.0, 10.0, 0.0, 190.0, 45.0),
        "DESCRIBE" to MacroRange(1791, 100.0, 1600.0, 550.0, 5.0, 80.0, 40.0, 5.0, 80.0, 15.0, 10.0, 150.0, 50.0),
        "QUESTION" to MacroRange(1408, 10.0, 500.0, 150.0, 0.5, 35.0, 15.0, 0.0, 45.0, 3.0, 0.0, 80.0, 18.0),
    )
}
