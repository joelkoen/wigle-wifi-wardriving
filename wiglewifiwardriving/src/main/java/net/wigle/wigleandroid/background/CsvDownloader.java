package net.wigle.wigleandroid.background;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.util.FileUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static net.wigle.wigleandroid.background.ObservationUploader.CSV_COLUMN_HEADERS;
import static net.wigle.wigleandroid.util.FileUtility.CSV_EXT;

/**
 * A CSV-upload grabber intended for re-import of CSV files
 * Created by arkasha on 01/25/20.
 */

public class CsvDownloader extends AbstractProgressApiRequest {
    private Status status;

    public CsvDownloader(final FragmentActivity context, final DatabaseHelper dbHelper /*TODO: not needed?*/,
                         final String transid, final ApiListener listener) {
        super(context, dbHelper, "CsvDL", transid+CSV_EXT, MainActivity.CSV_TRANSID_URL_STEM+transid, false,
                true, true, false, AbstractApiRequest.REQUEST_GET, listener, true);
        }

    @Override
    protected void subRun() throws IOException, InterruptedException, WiGLEAuthException {
        status = Status.UNKNOWN;
        final Bundle bundle = new Bundle();
        sendBundledMessage( Status.DOWNLOADING.ordinal(), bundle );

        String result = null;
        try {
            result = doDownload(this.connectionMethod);
            writeSharefile(result, outputFileName, true);
            sendBundledMessage( Status.PARSING.ordinal(), bundle );
            final File compressed = FileUtility.getCsvGzFile(context, outputFileName+FileUtility.GZ_EXT);
            if (null != compressed) {
                GZIPInputStream in = new GZIPInputStream(new FileInputStream(compressed));
                Reader decoder = new InputStreamReader(in);
                BufferedReader br = new BufferedReader(decoder);

                String line;
                boolean headerOneChecked = false;
                boolean headerTwoChecked = false;
                while ((line = br.readLine()) != null) {
                    if (!headerOneChecked) {
                        MainActivity.info("Giving Header One a pass");
                        MainActivity.info(line);
                        headerOneChecked = true;
                    } else if (!headerTwoChecked) {
                        if (line.contains(CSV_COLUMN_HEADERS)) {
                            headerTwoChecked = true;
                        } else {
                            MainActivity.error("CSV header check failed: " + line);
                        }
                    } else {
                        //TODO: process file line!
                        MainActivity.info(line+"\n");
                    }
                }

                final JSONObject json = new JSONObject("{success: " + true + ", file:\"" +
                        FileUtility.getCsvGzFile(context, outputFileName+FileUtility.GZ_EXT) + "\"}");
                sendBundledMessage(Status.SUCCESS.ordinal(), bundle);
                listener.requestComplete(json, false);
            } else {
                sendBundledMessage( Status.FAIL.ordinal(), bundle );
            }
        } catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            throw waex;
        } catch (final JSONException ex) {
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            MainActivity.error("ex: " + ex + " result: " + result, ex);
        } catch (final Exception e) {
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            MainActivity.error("ex: " + e + " result: " + result +" file: " +outputFileName , e);
        }
    }

    /**
     * write string data to a file accessible for Intent-based share or view
     * @param result the result of the operation
     * @param filename the filename to write
     * @throws IOException on failure to write
     */
    protected void writeSharefile(final String result, final String filename, final boolean compress) throws IOException {

        String outputIntermediate = null;
        if (FileUtility.hasSD()) {
            if (outputFileName != null) {
                //DEBUG: FileUtility.printDirContents(new File(MainActivity.getSDPath()));
                //ALIBI: for external-storage, our existing "cache" method is fine to write the file
                cacheResult(result);
                outputIntermediate = outputFileName;

                //DEBUG: FileUtility.printDirContents(new File(MainActivity.getSDPath()));
            }
        } else {
            //ALIBI: building a special directory for KML for intents
            // the app files directory might have been enough here, but helps with provider_paths

            //see if KML dir exists
            MainActivity.info("local storage DL...");

            File csvFile = FileUtility.getCsvGzFile(context, filename);
            outputIntermediate = csvFile.getAbsolutePath();
            FileOutputStream out = new FileOutputStream(csvFile);
            ObservationUploader.writeFos(out, result);
        }
        if (outputIntermediate != null && compress) {
            File intermediate = FileUtility.getCsvGzFile(context, outputIntermediate);
            if (intermediate != null) {
                FileInputStream in = new FileInputStream(intermediate);
                final String compressedFile = intermediate.getCanonicalPath() + FileUtility.GZ_EXT;

                GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(compressedFile));

                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.finish();
                out.close();
                boolean success = intermediate.delete();
                MainActivity.info("deleted result: "+success+". Intermediate file "+outputIntermediate + " completed export to "+compressedFile);
            } else {
                MainActivity.error("Unable to get file location for "+outputIntermediate);
            }
        } else {
            MainActivity.info("No compression requested; output "+outputIntermediate);
        }

    }
}
