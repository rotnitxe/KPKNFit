package com.example.kpkn.ui.components.rings

/*
 * Geometría del cuerpo del ajuste de Músculos (G6, LV10b): una figura limpia en un lienzo de 220 × 360 unidades y las zonas de los 13
 * pilares sobre ella. Cada forma es una lista plana de puntos x0, y0, x1, y1… que se cierra y se suaviza al dibujar (Catmull-Rom).
 * Las piezas de la figura y las zonas están dadas solo para el lado DERECHO del dibujo; la izquierda es su reflejo respecto a x = 110.
 * Datos puros: sin Compose ni Android, para poder probarlos.
 */
internal object RingAdjustBodyData {

    /** Cabeza: su mitad derecha, del eje (arriba) al eje (la barbilla). */
    val HeadRight: FloatArray = floatArrayOf(
        110f, 7f, 120f, 9f, 126f, 18f, 126f, 28f,
        123f, 38f, 117f, 46f, 110f, 49f,
    )

    /** Cuello: una pieza simétrica, ya completa. */
    val Neck: FloatArray = floatArrayOf(
        102f, 44f, 118f, 44f, 119f, 62f, 110f, 67f,
        101f, 62f,
    )

    /** Tronco, de hombros a pelvis: su mitad derecha, del cuello al eje. */
    val TorsoRight: FloatArray = floatArrayOf(
        118f, 61f, 132f, 63f, 148f, 67f, 160f, 72f,
        164f, 82f, 161f, 96f, 157f, 110f, 153f, 124f,
        149f, 138f, 146f, 150f, 148f, 162f, 153f, 174f,
        155f, 186f, 146f, 196f, 128f, 202f, 110f, 204f,
    )

    /** Brazo derecho, del hombro a la mano; el izquierdo es su reflejo. */
    val ArmRight: FloatArray = floatArrayOf(
        156f, 70f, 168f, 72f, 177f, 82f, 181f, 98f,
        182f, 114f, 181f, 130f, 182f, 144f, 185f, 158f,
        186f, 172f, 184f, 186f, 183f, 198f, 184f, 210f,
        182f, 224f, 177f, 232f, 172f, 227f, 171f, 212f,
        170f, 200f, 168f, 186f, 166f, 172f, 163f, 158f,
        161f, 146f, 159f, 134f, 158f, 118f, 158f, 102f,
        157f, 86f,
    )

    /** Pierna derecha, de la cadera al pie; la izquierda es su reflejo. */
    val LegRight: FloatArray = floatArrayOf(
        153f, 172f, 158f, 190f, 158f, 214f, 155f, 240f,
        151f, 262f, 149f, 280f, 151f, 296f, 150f, 316f,
        146f, 334f, 147f, 346f, 155f, 353f, 137f, 355f,
        129f, 350f, 128f, 334f, 126f, 316f, 124f, 298f,
        122f, 280f, 122f, 258f, 121f, 236f, 117f, 216f,
        110f, 204f, 118f, 194f, 136f, 184f,
    )

    /** Zonas de la vista de frente: Deltoides, Pectorales, Bíceps, Abdomen, Core (los oblicuos), Cuádriceps y Pantorrillas. */
    val FrontZones: List<RingAdjustBodyZone> = listOf(
        RingAdjustBodyZone(
            pillar = "Deltoides",
            shapes = listOf(
                floatArrayOf(
                    160f, 71f, 170f, 74f, 178f, 86f, 179f, 98f,
                    172f, 108f, 165f, 104f, 161f, 92f, 158f, 80f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Pectorales",
            shapes = listOf(
                floatArrayOf(
                    113f, 77f, 128f, 75f, 144f, 78f, 156f, 88f,
                    155f, 100f, 146f, 110f, 130f, 114f, 113f, 110f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Bíceps",
            shapes = listOf(
                floatArrayOf(
                    166f, 112f, 176f, 112f, 180f, 124f, 178f, 140f,
                    172f, 150f, 165f, 144f, 162f, 128f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Abdomen",
            shapes = listOf(
                floatArrayOf(
                    117f, 120f, 123f, 120f, 127f, 124f, 127f, 127f,
                    123f, 131f, 117f, 131f, 113f, 127f, 113f, 124f,
                ),
                floatArrayOf(
                    117f, 133.5f, 123f, 133.5f, 127f, 137.5f, 127f, 140.5f,
                    123f, 144.5f, 117f, 144.5f, 113f, 140.5f, 113f, 137.5f,
                ),
                floatArrayOf(
                    117f, 147f, 122.5f, 147f, 126.5f, 151f, 126.5f, 154f,
                    122.5f, 158f, 117f, 158f, 113f, 154f, 113f, 151f,
                ),
                floatArrayOf(
                    117f, 160.5f, 121f, 160.5f, 125f, 164.5f, 125f, 167f,
                    121f, 171f, 117f, 171f, 113f, 167f, 113f, 164.5f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Core",
            shapes = listOf(
                floatArrayOf(
                    135f, 124f, 146f, 130f, 148f, 144f, 146f, 160f,
                    140f, 168f, 136f, 156f, 137f, 140f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Cuádriceps",
            shapes = listOf(
                floatArrayOf(
                    117f, 202f, 130f, 194f, 146f, 198f, 155f, 212f,
                    155f, 240f, 149f, 262f, 141f, 270f, 131f, 266f,
                    124f, 248f, 120f, 224f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Pantorrillas",
            shapes = listOf(
                floatArrayOf(
                    134f, 290f, 146f, 288f, 150f, 304f, 147f, 322f,
                    142f, 334f, 134f, 326f, 130f, 308f,
                ),
            ),
        ),
    )

    /** Zonas de la vista de espalda: Trapecio, Dorsales, Deltoides (el posterior), Tríceps, Erectores Espinales, Glúteos, Isquiosurales y Pantorrillas. */
    val BackZones: List<RingAdjustBodyZone> = listOf(
        RingAdjustBodyZone(
            pillar = "Trapecio",
            shapes = listOf(
                floatArrayOf(
                    112f, 62f, 126f, 64f, 146f, 70f, 158f, 78f,
                    146f, 94f, 130f, 112f, 112f, 124f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Dorsales",
            shapes = listOf(
                floatArrayOf(
                    132f, 106f, 150f, 96f, 158f, 108f, 154f, 128f,
                    146f, 146f, 134f, 152f, 126f, 134f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Deltoides",
            shapes = listOf(
                floatArrayOf(
                    160f, 71f, 170f, 74f, 178f, 86f, 179f, 98f,
                    172f, 108f, 165f, 104f, 161f, 92f, 158f, 80f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Tríceps",
            shapes = listOf(
                floatArrayOf(
                    166f, 108f, 176f, 108f, 180f, 122f, 178f, 140f,
                    172f, 150f, 165f, 144f, 162f, 126f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Erectores Espinales",
            shapes = listOf(
                floatArrayOf(
                    113f, 128f, 121f, 128f, 125f, 146f, 125f, 164f,
                    120f, 174f, 113f, 172f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Glúteos",
            shapes = listOf(
                floatArrayOf(
                    113f, 176f, 132f, 172f, 151f, 180f, 157f, 196f,
                    148f, 212f, 130f, 216f, 113f, 208f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Isquiosurales",
            shapes = listOf(
                floatArrayOf(
                    122f, 224f, 140f, 222f, 152f, 232f, 152f, 252f,
                    146f, 272f, 136f, 278f, 126f, 268f, 122f, 244f,
                ),
            ),
        ),
        RingAdjustBodyZone(
            pillar = "Pantorrillas",
            shapes = listOf(
                floatArrayOf(
                    134f, 290f, 146f, 288f, 150f, 304f, 147f, 322f,
                    142f, 334f, 134f, 326f, 130f, 308f,
                ),
            ),
        ),
    )

    /** Las zonas de una vista. */
    fun zonesOf(view: RingAdjustBodyView): List<RingAdjustBodyZone> =
        if (view == RingAdjustBodyView.FRONT) FrontZones else BackZones
}
