/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.log4j.chainsaw;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


/**
 * GUI panel used to manipulate the PreferenceModel for a Log Panel
 *
 * @author Paul Smith
 */
public class LogPanelPreferencePanel extends AbstractPreferencePanel
{
  //~ Instance fields =========================================================

  private final LogPanelPreferenceModel preferenceModel;
  private final ModifiableListModel columnListModel = new ModifiableListModel();
  private static final Logger logger = LogManager.getLogger(LogPanelPreferencePanel.class);

  //~ Constructors ============================================================

  public LogPanelPreferencePanel(LogPanelPreferenceModel model)
  {
    preferenceModel = model;
    initComponents();

    getOkButton().addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          hidePanel();
        }
      });

    getCancelButton().addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          hidePanel();
        }
      });
    }

  //~ Methods =================================================================

  /**
   * DOCUMENT ME!
   *
   * @param args DOCUMENT ME!
   */
  public static void main(String[] args)
  {
    JFrame f = new JFrame("Preferences Panel Test Bed");
    LogPanelPreferenceModel model = new LogPanelPreferenceModel();
    LogPanelPreferencePanel panel = new LogPanelPreferencePanel(model);
    f.getContentPane().add(panel);

    model.addPropertyChangeListener(new PropertyChangeListener()
      {
        public void propertyChange(PropertyChangeEvent evt)
        {
          logger.warn(evt.toString());
        }
      });
    panel.setOkCancelActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          System.exit(1);
        }
      });

    f.setSize(640, 480);
    f.setVisible(true);
  }

  protected TreeModel createTreeModel()
  {
    final DefaultMutableTreeNode rootNode =
      new DefaultMutableTreeNode("Preferences");
    DefaultTreeModel model = new DefaultTreeModel(rootNode);

    DefaultMutableTreeNode visuals =
      new DefaultMutableTreeNode(new VisualsPrefPanel());
    DefaultMutableTreeNode formatting =
      new DefaultMutableTreeNode(new FormattingPanel());
    DefaultMutableTreeNode columns =
      new DefaultMutableTreeNode(new ColumnSelectorPanel());

    rootNode.add(visuals);
    rootNode.add(formatting);
    rootNode.add(columns);

    return model;
  }

  //~ Inner Classes ===========================================================

  /**
   * Allows the user to choose which columns to display.
   *
   * @author Paul Smith
   *
   */
  public class ColumnSelectorPanel extends BasicPrefPanel
  {
    //~ Constructors ==========================================================

    ColumnSelectorPanel()
    {
      super("Columns");
      initComponents();
    }

    //~ Methods ===============================================================

    private void initComponents()
    {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      Box columnBox = new Box(BoxLayout.Y_AXIS);

      //		columnBox.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Displayed Columns"));
      final JList columnList = new JList();
      columnList.setVisibleRowCount(10);

      for (
        Iterator iter = preferenceModel.getColumns().iterator();
          iter.hasNext();)
      {
        columnListModel.addElement(iter.next());
      }

      columnList.setModel(columnListModel);

      CheckListCellRenderer cellRenderer = new CheckListCellRenderer()
        {
          protected boolean isSelected(Object value)
          {
            return LogPanelPreferencePanel.this.preferenceModel.isColumnVisible((TableColumn)
              value);
          }
        };

      columnList.addMouseListener(new MouseAdapter()
        {
          public void mouseClicked(MouseEvent e)
          {
            if (
              (e.getClickCount() > 1)
                && ((e.getModifiers() & InputEvent.BUTTON1_MASK) > 0))
            {
              int i = columnList.locationToIndex(e.getPoint());

              if (i >= 0)
              {
                Object column = columnListModel.get(i);
                preferenceModel.toggleColumn(((TableColumn)column));
              }
            }
          }
        });
      columnList.setCellRenderer(cellRenderer);
      columnBox.add(new JScrollPane(columnList));

      add(columnBox);
      add(Box.createVerticalGlue());
    }
  }

  /**
   * Provides preference gui's for all the Formatting options
   * available for the columns etc.
   */
  private class FormattingPanel extends BasicPrefPanel
  {
    //~ Instance fields =======================================================

    private JTextField customFormatText = new JTextField("", 10);
    private JTextField loggerPrecision = new JTextField(5);
    private JRadioButton rdCustom = new JRadioButton("Custom Format");
    private final JRadioButton rdISO =
      new JRadioButton(
        "<html><b>Fast</b> ISO 8601 format (yyyy-MM-dd HH:mm:ss)</html>");
    private final JRadioButton rdLevelIcons = new JRadioButton("Icons");
    private final JRadioButton rdLevelText = new JRadioButton("Text");
    private JRadioButton rdLast;

    //~ Constructors ==========================================================

    private FormattingPanel()
    {
      super("Formatting");
      this.initComponents();
      setupListeners();
    }

    //~ Methods ===============================================================

    private void initComponents()
    {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      JPanel dateFormatPanel = new JPanel();
      dateFormatPanel.setBorder(
        BorderFactory.createTitledBorder(
          BorderFactory.createEtchedBorder(), "Timestamp"));
      dateFormatPanel.setLayout(
        new BoxLayout(dateFormatPanel, BoxLayout.Y_AXIS));
      dateFormatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

      customFormatText.setPreferredSize(new Dimension(100, 20));
      customFormatText.setMaximumSize(customFormatText.getPreferredSize());
      customFormatText.setMinimumSize(customFormatText.getPreferredSize());
      customFormatText.setEnabled(false);

      rdCustom.setSelected(preferenceModel.isCustomDateFormat());

      ButtonGroup bgDateFormat = new ButtonGroup();

      rdISO.setAlignmentX(0);
      rdISO.setSelected(preferenceModel.isUseISO8601Format());

      bgDateFormat.add(rdISO);
      dateFormatPanel.add(rdISO);

      for (
        Iterator iter = LogPanelPreferenceModel.DATE_FORMATS.iterator();
          iter.hasNext();)
      {
        final String format = (String) iter.next();
        final JRadioButton rdFormat = new JRadioButton(format);
        rdFormat.setAlignmentX(0);

        bgDateFormat.add(rdFormat);
        rdFormat.addActionListener(new ActionListener()
          {
            public void actionPerformed(ActionEvent e)
            {
              preferenceModel.setDateFormatPattern(format);
              customFormatText.setEnabled(rdCustom.isSelected());
              rdLast = rdFormat;
            }
          });
        //update based on external changes to dateformatpattern (column context
        //menu)
        preferenceModel.addPropertyChangeListener(
          "dateFormatPattern", new PropertyChangeListener()
          {
            public void propertyChange(PropertyChangeEvent evt)
            {
              rdFormat.setSelected(
                preferenceModel.getDateFormatPattern().equals(format));
              rdLast = rdFormat;
            }
          });

        dateFormatPanel.add(rdFormat);
      }

      // add a custom date format
      if (preferenceModel.isCustomDateFormat())
      {
        customFormatText.setText(preferenceModel.getDateFormatPattern());
        customFormatText.setEnabled(true);
      }

      rdCustom.setAlignmentX(0);
      bgDateFormat.add(rdCustom);

      Box customBox = Box.createHorizontalBox();

      //      Following does not work in JDK 1.3.1
      //      customBox.setAlignmentX(0);
      customBox.add(rdCustom);
      customBox.add(customFormatText);
      customBox.add(Box.createHorizontalGlue());
      dateFormatPanel.add(customBox);

      //      dateFormatPanel.add(Box.createVerticalGlue());
      add(dateFormatPanel);

      JPanel levelFormatPanel = new JPanel();
      levelFormatPanel.setLayout(
        new BoxLayout(levelFormatPanel, BoxLayout.Y_AXIS));
      levelFormatPanel.setBorder(
        BorderFactory.createTitledBorder(
          BorderFactory.createEtchedBorder(), "Level"));
      levelFormatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

      ButtonGroup bgLevel = new ButtonGroup();
      bgLevel.add(rdLevelIcons);
      bgLevel.add(rdLevelText);

      rdLevelIcons.setSelected(preferenceModel.isLevelIcons());

      levelFormatPanel.add(rdLevelIcons);
      levelFormatPanel.add(rdLevelText);

      add(levelFormatPanel);

      JPanel loggerFormatPanel = new JPanel();
      loggerFormatPanel.setLayout(
        new BoxLayout(loggerFormatPanel, BoxLayout.Y_AXIS));
      loggerFormatPanel.setBorder(
        BorderFactory.createTitledBorder(
          BorderFactory.createEtchedBorder(), "Logger"));
      loggerFormatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

      final JLabel precisionLabel =
        new JLabel("Number of package levels to hide)");
      final JLabel precisionLabel2 =
        new JLabel("leave blank to display full logger");

      loggerFormatPanel.add(precisionLabel);
      loggerFormatPanel.add(precisionLabel2);

      JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));

      p.add(loggerPrecision);
      loggerFormatPanel.add(p);

      add(loggerFormatPanel);

      add(Box.createVerticalGlue());
    }

    /*
     * Restore text fields to current model values
     */
    private void reset() {

    	if (preferenceModel.isCustomDateFormat()) {
    		customFormatText.setText(preferenceModel.getDateFormatPattern());
    	} else {
    		if (rdLast != null) {
    			rdLast.setSelected(true);
    		}
    		customFormatText.setEnabled(false);
    	}
    	
    	loggerPrecision.setText(preferenceModel.getLoggerPrecision());
    }

    /*
     * Commit text fields to model
     */
    private void commit() {
    	if (rdCustom.isSelected()) {
    		preferenceModel.setDateFormatPattern(customFormatText.getText());
    	}
    	preferenceModel.setLoggerPrecision(loggerPrecision.getText());
    }

    /**
     * DOCUMENT ME!
    */
    private void setupListeners()
    {
      getOkButton().addActionListener(new ActionListener() {
    	  public void actionPerformed(ActionEvent evt) {
    		  commit();
    	  }
      });
      
      getCancelButton().addActionListener(new ActionListener() {
    	  public void actionPerformed(ActionEvent evt) {
    		  reset();
    	  }
      });
    	  
    	  rdCustom.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            customFormatText.setEnabled(rdCustom.isSelected());
            customFormatText.grabFocus();
          }
        });

      //a second?? listener for dateformatpattern
      preferenceModel.addPropertyChangeListener(
        "dateFormatPattern", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            /**
             * we need to make sure we are not reacting to the user typing, so only do this
             * if the text box is not the same as the model
             */
            if (
              preferenceModel.isCustomDateFormat()
                && !customFormatText.getText().equals(
                  evt.getNewValue().toString()))
            {
              customFormatText.setText(preferenceModel.getDateFormatPattern());
              rdCustom.setSelected(true);
              customFormatText.setEnabled(true);
            }
            else
            {
              rdCustom.setSelected(false);
            }
          }
        });

      rdISO.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            preferenceModel.setDateFormatPattern("ISO8601");
            customFormatText.setEnabled(rdCustom.isSelected());
            rdLast = rdISO;
          }
        });
      preferenceModel.addPropertyChangeListener(
        "dateFormatPattern", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            rdISO.setSelected(preferenceModel.isUseISO8601Format());
            rdLast = rdISO;
          }
        });

      ActionListener levelIconListener = new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            preferenceModel.setLevelIcons(rdLevelIcons.isSelected());
          }
        };

      rdLevelIcons.addActionListener(levelIconListener);
      rdLevelText.addActionListener(levelIconListener);

      preferenceModel.addPropertyChangeListener(
        "levelIcons", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            boolean value = ((Boolean) evt.getNewValue()).booleanValue();
            rdLevelIcons.setSelected(value);
            rdLevelText.setSelected(!value);
          }
        });
    }
  }

  /**
   * DOCUMENT ME!
   *
   * @author $author$
   * @version $Revision$, $Date$
   *
   * @author psmith
   *
   */
  private class VisualsPrefPanel extends BasicPrefPanel
  {
    //~ Instance fields =======================================================

    private final JCheckBox detailPanelVisible =
      new JCheckBox("Show Event Detail panel");

    private final JCheckBox loggerTreePanel =
      new JCheckBox("Show Logger Tree panel");
    private final JCheckBox scrollToBottom =
      new JCheckBox("Scroll to bottom (view tracks with new events)");
    private final JCheckBox toolTips =
      new JCheckBox("Show Event Detail Tooltips");

    //~ Constructors ==========================================================

    /**
     * Creates a new VisualsPrefPanel object.
    */
    private VisualsPrefPanel()
    {
      super("Visuals");
      initPanelComponents();
      setupListeners();
    }

    //~ Methods ===============================================================

    /**
     * DOCUMENT ME!
    */
    private void initPanelComponents()
    {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      add(toolTips);
      add(detailPanelVisible);
      add(loggerTreePanel);
      add(scrollToBottom);

      toolTips.setSelected(preferenceModel.isToolTips());
      detailPanelVisible.setSelected(preferenceModel.isDetailPaneVisible());
      loggerTreePanel.setSelected(preferenceModel.isLogTreePanelVisible());
    }

    /**
     * DOCUMENT ME!
    */
    private void setupListeners()
    {
      toolTips.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            preferenceModel.setToolTips(toolTips.isSelected());
          }
        });
      preferenceModel.addPropertyChangeListener(
        "toolTips", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            boolean value = ((Boolean) evt.getNewValue()).booleanValue();
            toolTips.setSelected(value);
          }
        });

      detailPanelVisible.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            preferenceModel.setDetailPaneVisible(detailPanelVisible.isSelected());
          }
        });

      preferenceModel.addPropertyChangeListener(
        "detailPaneVisible", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            boolean value = ((Boolean) evt.getNewValue()).booleanValue();
            detailPanelVisible.setSelected(value);
          }
        });

      scrollToBottom.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            preferenceModel.setScrollToBottom(scrollToBottom.isSelected());
          }
        });

      preferenceModel.addPropertyChangeListener(
        "scrollToBottom", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            boolean value = ((Boolean) evt.getNewValue()).booleanValue();
            scrollToBottom.setSelected(value);
          }
        });

      loggerTreePanel.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            preferenceModel.setLogTreePanelVisible(loggerTreePanel.isSelected());
          }
        });

      preferenceModel.addPropertyChangeListener(
        "logTreePanelVisible", new PropertyChangeListener()
        {
          public void propertyChange(PropertyChangeEvent evt)
          {
            boolean value = ((Boolean) evt.getNewValue()).booleanValue();
            loggerTreePanel.setSelected(value);
          }
        });

      preferenceModel.addPropertyChangeListener("columns", new PropertyChangeListener() {
        	public void propertyChange(PropertyChangeEvent evt) {
      	  List cols = (List)evt.getNewValue();
            for (
          	        Iterator iter = cols.iterator();
          	          iter.hasNext();)
          	      {
          	        TableColumn col = (TableColumn) iter.next();
          	        if (!columnListModel.contains(col)) {
          	        	columnListModel.addElement(col);
          	            columnListModel.fireContentsChanged();
          	        }
          	      }
        }});

        preferenceModel.addPropertyChangeListener(
                "visibleColumns", new PropertyChangeListener()
                {
                  public void propertyChange(PropertyChangeEvent evt)
                  {
                    columnListModel.fireContentsChanged();
                  }
                });

    }
  }
}
