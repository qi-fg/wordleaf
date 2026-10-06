package cn.wordleaf.cet6;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants the system installer read access to this one private APK. */
public class UpdateProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        if (!"/update.apk".equals(uri.getPath())) throw new FileNotFoundException();
        return new File(getContext().getCacheDir(), "update.apk");
    }
    public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f=file(uri); String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor c=new MatrixCursor(cols); Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++) row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?"wordleaf-update.apk":OpenableColumns.SIZE.equals(cols[i])?f.length():null;
            c.addRow(row); return c;
        } catch(FileNotFoundException e) { return null; }
    }
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
