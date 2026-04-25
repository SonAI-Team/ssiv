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
    companion object {
        private const val TILE_SIZE_RATIO = 1.25f
    }

    var tileMap: MutableMap<Int, List<Tile>>? = null
        private set

    data class TileMapInitParams(
        val maxTileDimensions: Point,
        val fullImageSampleSize: Int,
        val sWidth: Int,
        val sHeight: Int,
        val viewWidth: Int,
        val viewHeight: Int
    )

    fun initialiseTileMap(params: TileMapInitParams) {
        val newTileMap = LinkedHashMap<Int, List<Tile>>()
        var sampleSize = params.fullImageSampleSize
        var xTiles = 1
        var yTiles = 1
        while (true) {
            var sTileWidth = params.sWidth / xTiles
            var sTileHeight = params.sHeight / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > params.maxTileDimensions.x ||
                (subTileWidth > params.viewWidth * TILE_SIZE_RATIO && sampleSize < params.fullImageSampleSize)
            ) {
                xTiles += 1
                sTileWidth = params.sWidth / xTiles
                subTileWidth = sTileWidth / sampleSize
            }
            while (subTileHeight + yTiles + 1 > params.maxTileDimensions.y ||
                (subTileHeight > params.viewHeight * TILE_SIZE_RATIO && sampleSize < params.fullImageSampleSize)
            ) {
                yTiles += 1
                sTileHeight = params.sHeight / yTiles
                subTileHeight = sTileHeight / sampleSize
            }
            val tileGrid = ArrayList<Tile>(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == params.fullImageSampleSize
                    tile.sRect = Rect(
                        x * sTileWidth,
                        y * sTileHeight,
                        if (x == xTiles - 1) params.sWidth else (x + 1) * sTileWidth,
                        if (y == yTiles - 1) params.sHeight else (y + 1) * sTileHeight
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

    data class RefreshParams(
        val load: Boolean,
        val scale: Float,
        val fullImageSampleSize: Int,
        val decoder: ImageRegionDecoder?,
        val sVisRect: RectF,
        val calculateInSampleSize: (Float) -> Int,
        val executeTileLoadTask: (ImageRegionDecoder, Tile) -> Unit
    )

    fun refreshRequiredTiles(params: RefreshParams) {
        val currentTileMap = tileMap
        if (params.decoder == null || currentTileMap == null) return

        val sampleSize = min(params.fullImageSampleSize, params.calculateInSampleSize(params.scale))

        for (tileMapEntry in currentTileMap.entries) {
            for (tile in tileMapEntry.value) {
                updateTile(tile, sampleSize, params)
            }
        }
    }

    private fun updateTile(
        tile: Tile,
        sampleSize: Int,
        params: RefreshParams
    ) {
        if (tile.sampleSize < sampleSize ||
            (tile.sampleSize > sampleSize && tile.sampleSize != params.fullImageSampleSize)
        ) {
            tile.visible = false
            tile.bitmap?.recycle()
            tile.bitmap = null
        }
        if (tile.sampleSize == sampleSize) {
            if (tileVisible(tile, params.sVisRect)) {
                tile.visible = true
                if (!tile.loading && tile.bitmap == null && params.load) {
                    params.executeTileLoadTask(params.decoder!!, tile)
                }
            } else if (tile.sampleSize != params.fullImageSampleSize) {
                tile.visible = false
                tile.bitmap?.recycle()
                tile.bitmap = null
            }
        } else if (tile.sampleSize == params.fullImageSampleSize) {
            tile.visible = true
        }
    }

    private fun tileVisible(tile: Tile, sVisRect: RectF): Boolean {
        val sRect = tile.sRect ?: return false
        return !(sVisRect.left > sRect.right || sRect.left > sVisRect.right ||
                sVisRect.top > sRect.bottom || sRect.top > sVisRect.bottom)
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
