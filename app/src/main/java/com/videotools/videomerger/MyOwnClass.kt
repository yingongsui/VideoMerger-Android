package com.videotools.videomerger

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

object MyOwnClass {

    fun getRealPath(fileUri: Uri): String? {
        val DocId = DocumentsContract.getDocumentId(fileUri)    //获取uri
        val split = DocId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()   //分割获取的uri信息
        return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
    }


    //问题就在这个getDataColumn上
    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null       //Cursor是数据类
        val column = "_data"
        val projection = arrayOf(column)   //将column变为array
        try {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        //return null
        return "Wrong in getDataColum"
        //return context.toString()
        //return column
    }

    //判断文件类型
    fun FilePositionType(uri:Uri): String {
        return when (uri.authority) {   //用authority判断授权者名称
            "com.android.externalstorage.documents" -> "Document"

            "com.android.providers.downloads.documents" -> "Download"

            "com.android.providers.media.documents" -> "Media"

            else -> "Wrong in Deciding File Type"
        }
    }

    fun getVideoInfo(mediaInfo:String): Array<Int>{
        val widthline = Regex("\"width\": \\d+").findAll(mediaInfo).map(MatchResult::value).toList()
        val heightline = Regex("\"height\": \\d+").findAll(mediaInfo).map(MatchResult::value).toList()
        val framesline = Regex("\"nb_frames\": \"\\d+").findAll(mediaInfo).map(MatchResult::value).toList()

        val width = widthline[0].replace("\"width\": ","").toInt()
        val height = heightline[0].replace("\"height\": ","").toInt()
        val frames = framesline[0].replace("\"nb_frames\": \"","").toInt()
        return arrayOf(width,height,frames)
    }


}