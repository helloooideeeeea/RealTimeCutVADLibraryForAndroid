package io.codeconcept.realtimecutvadlibrary;

import android.content.Context;
import java.io.*;
import java.util.UUID;

public class ResourceHelper {

    /**
     * Raw リソースをキャッシュディレクトリにコピーする
     *
     * @param context   アプリケーションコンテキスト
     * @param resId     Raw リソース ID (R.raw.xxx)
     * @param extension 生成するファイルの拡張子 (例: "pcm", "onnx")
     * @return コピーされたファイルオブジェクト (成功時) / null (失敗時)
     */
    public static File copyRawResourceToFile(Context context, int resId, String extension) {
        // ランダムなファイル名を生成（拡張子を指定）
        String randomFileName = UUID.randomUUID().toString() + "." + extension;

        File outputFile = new File(context.getCacheDir(), randomFileName);

        try (InputStream inputStream = context.getResources().openRawResource(resId);
             OutputStream outputStream = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            return outputFile;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
