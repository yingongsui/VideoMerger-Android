package com.videotools.videomerger

import android.R
import android.content.Context
import android.database.Cursor
import android.media.MediaDrm.PlaybackComponent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.ListView
import android.widget.SimpleAdapter
import androidx.arch.core.util.Function
import androidx.constraintlayout.widget.Placeholder
import java.io.File

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
    fun filePositionType(uri:Uri): String {
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
        val timebaseline = Regex("\"time_base\": \"1/\\d+").findAll(mediaInfo).map(MatchResult::value).toList()

        val width = widthline[0].replace("\"width\": ","").toInt()
        val height = heightline[0].replace("\"height\": ","").toInt()
        val frames = framesline[0].replace("\"nb_frames\": \"","").toInt()
        val timebase = timebaseline[0].replace("\"time_base\": \"1/","").toInt()
        return arrayOf(width,height,frames,timebase)
    }

    //获取listview中的所有信息
    fun allfileinlist(listview:ListView):MutableList<MutableMap<String, Any>> {
        var listcontents: MutableList<MutableMap<String, Any>> = mutableListOf()

        if (listview.adapter != null) {
            val filenum = listview.adapter.count  //记录文件数目
            for (i in 0 until filenum) {
                listcontents.add(listview.getItemAtPosition(i) as MutableMap<String, Any>)
            }
            return listcontents
        }
        else{
            return mutableListOf()
        }
    }

    //对信息进行各种处理
    fun filelisttreat(list:MutableList<MutableMap<String, Any>>, Cho : String = "default",Row : Int = 0):MutableList<MutableMap<String, Any>>{
        var newlist: MutableList<MutableMap<String, Any>> = mutableListOf()
        if(Cho == "NoNum"){
            for(i in 0 until list.size) {
                newlist.add(mutableMapOf("filepath" to list[i]["filepath"].toString().replaceBefore("/", ""), "filedet" to list[i]["filedet"].toString() ))
            }
        }
        else if(Cho == "AddNum"){
            for(i in 0 until list.size ) {
                newlist.add(mutableMapOf("filepath" to "%d. ".format(i+1) + list[i]["filepath"].toString(), "filedet" to list[i]["filedet"].toString() ))
            }
        }
        else if(Cho == "Frames"){
            for(i in 0 until list.size ) {
                newlist.add(mutableMapOf("filepath" to list[i]["filepath"].toString().replaceBefore("/", ""), "filedet" to list[i]["filedet"].toString().split(",")[1].filter { it.isDigit() }.toInt() ))

            }
        }
        else if(Cho == "Remove"){
            newlist = list
            newlist.removeAt(Row)
        }

        return newlist
    }

}