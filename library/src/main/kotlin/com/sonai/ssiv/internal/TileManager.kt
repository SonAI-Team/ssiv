package com.sonai.ssiv.internal

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import com.sonai.ssiv.decoder.ImageRegionDecoder
import kotlin.math.min

/**
 * Manages the tiles for SubsamplingScaleImageView.
 */
class TileManager {
    var tileMap: MutableMap<Int, List<Tile>>? = null
        private set

    fun initialiseTileMap(
        maxTileDimensions: Point,
        fullImageSampleSize: Int,
        sWidth: Int,
        sHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val newTileMap = LinkedHashMap<Int, List<Tile>>()
        var sampleSize = fullImageSampleSize
        var xTiles = 1
        var yTiles = 1
        while (true) {
            var sTileWidth = sWidth / xTiles
            var sTileHeight = sHeight / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x || subTileWidth > viewWidth * 1.25 && sampleSize < fullImageSampleSize) {
                xTiles += 1
                sTileWidth = sWidth / xTiles
                subTileWidth = sTileWidth / sampleSize
            }
            while (subTileHeight + yTiles + 1 > maxTileDimensions.y || subTileHeight > viewHeight * 1.25 && sampleSize < fullImageSampleSize) {
                yTiles += 1
                sTileHeight = sHeight / yTiles
                subTileHeight = sTileHeight / sampleSize
            }
            val tileGrid = ArrayList<Tile>(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == fullImageSampleSize
                    tile.sRect = Rect(
                        x * sTileWidth,
                        y * sTileHeight,
                        if (x == xTiles - 1) sWidth else (x + 1) * sTileWidth,
                        if (y == yTiles - 1) sHeight else (y + 1) * sTileHeight
                    )
                    tile.vRect = Rect()
                    tile.fileSRect = Rect(tile.sRect!!)
                    tileGrid.add(tile)
                }
            }
            newTileMap[sampleSize] = tileGrid
            if (sampleSize == 1) {
                break
            } else {
                sampleSize /= 2
            }
        }
        this.tileMap = newTileMap
    }

    fun refreshRequiredTiles(
        load: Boolean,
        scale: Float,
        fullImageSampleSize: Int,
        decoder: ImageRegionDecoder?,
        viewWidth: Int,
        viewHeight: Int,
        sVisRect: RectF,
        calculateInSampleSize: (Float) -> Int,
        executeTileLoadTask: (ImageRegionDecoder, Tile) -> Unit
    ) {
        if (decoder == null || tileMap == null) return

        val sampleSize = min(fullImageSampleSize, calculateInSampleSize(scale))

        for (tileMapEntry in tileMap!!.entries) {
            for (tile in tileMapEntry.value) {
                if (tile.sampleSize < sampleSize || (tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize)) {
                    tile.visible = false
                    tile.bitmap?.recycle()
                    tile.bitmap = null
                }
                if (tile.sampleSize == sampleSize) {
                    if (tileVisible(tile, sVisRect)) {
                        tile.visible = true
                        if (!tile.loading && tile.bitmap == null && load) {
                            executeTileLoadTask(decoder, tile)
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false
                        tile.bitmap?.recycle()
                        tile.bitmap = null
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true
                }
            }
        }
    }

    private fun tileVisible(tile: Tile, sVisRect: RectF): Boolean {
        return !(sVisRect.left > tile.sRect!!.right || tile.sRect!!.left > sVisRect.right ||
                sVisRect.top > tile.sRect!!.bottom || tile.sRect!!.top > sVisRect.bottom)
    }

    fun clear() {
        tileMap?.values?.forEach { tiles ->
            tiles.forEach { tile ->
                tile.bitmap?.recycle()
                tile.bitmap = null
            }
        }
        tileMap = null
    }
}
