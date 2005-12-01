package org.apache.log4j.chainsaw;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.apache.log4j.chainsaw.prefs.LoadSettingsEvent;
import org.apache.log4j.chainsaw.prefs.MRUFileList;
import org.apache.log4j.chainsaw.prefs.SaveSettingsEvent;
import org.apache.log4j.chainsaw.prefs.SettingsListener;
import org.apache.log4j.chainsaw.prefs.SettingsManager;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * Helper class that helps delegate the work of loading and saving the  values
 * of the ApplicationPreferenceModel, allowing that class to remain a simple
 * bean.
 * 
 * The Model passed to this class' constructor is the instance of the ApplicationPreference
 * that will be saved, and will have properties modified by loading from the
 * 'chainsaw.xml' file in the .chainsaw directory of the user's home directory.
 * 
 * @author psmith
 *
 */
public class ApplicationPreferenceModelSaver implements SettingsListener {

    private final ApplicationPreferenceModel model;
    
    /**
     * @param model
     */
    public ApplicationPreferenceModelSaver(final ApplicationPreferenceModel model) {
        this.model = model;
    }

    public void loadSettings(LoadSettingsEvent event) {
        XStream stream = new XStream(new DomDriver());
        try {
            File file = getApplicationPreferenceXMLFile(SettingsManager.getInstance().getSettingsDirectory());
            if (file.exists()) {
                FileReader reader = new FileReader(file);
                ApplicationPreferenceModel loadedModel = (ApplicationPreferenceModel) stream
                        .fromXML(reader);
                model.apply(loadedModel);
                reader.close();
            }
        } catch (Exception e) {
//            TODO exception handling
            e.printStackTrace();
        }

    }

    public void saveSettings(SaveSettingsEvent event) {
        XStream stream = new XStream(new DomDriver());
        try {
            File file = getApplicationPreferenceXMLFile(event.getSettingsLocation());
            FileWriter writer = new FileWriter(file);
            stream.toXML(model, writer);
            writer.close();
        } catch (Exception e) {
//            TODO exception handling
            e.printStackTrace();
        }

    }

    private File getApplicationPreferenceXMLFile(File settingsLocation) {
        return new File(settingsLocation, "chainsaw.xml");
    }

}
