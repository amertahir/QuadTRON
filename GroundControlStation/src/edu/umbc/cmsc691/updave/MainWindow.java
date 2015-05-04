package edu.umbc.cmsc691.updave;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.json.JSONException;
import org.json.JSONObject;

import com.digi.xbee.api.DigiMeshDevice;
import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.models.XBee64BitAddress;
import com.digi.xbee.api.models.XBeeMessage;
import com.digi.xbee.api.utils.ByteUtils;
import com.esri.runtime.ArcGISRuntime;
import com.esri.toolkit.overlays.NavigatorOverlay;
import com.esri.toolkit.overlays.ScaleBarOverlay;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.symbol.SimpleMarkerSymbol.Style;
import com.esri.core.symbol.Symbol;
import com.esri.map.GraphicsLayer;
import com.esri.map.JMap;
import com.esri.map.LocationOnMap;
import com.esri.map.MapEvent;
import com.esri.map.MapEventListenerAdapter;
import com.esri.map.MapOptions;
import com.esri.map.MapOptions.MapType;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JButton;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JRadioButton;
import javax.swing.AbstractAction;
import javax.swing.Action;

public class MainWindow implements IDataReceiveListener {

	// XBee stuff
	/* Constants */
    // Port where the XBee module is connected to.
    private static final String PORT = "COM6";
    // Baud rate of the XBee module.
    private static final int BAUD_RATE = 115200;
    // XBee MACs
    public static String baseStationMAC = "0013A200408BA39A";
    public static String uav1MAC = "0013A20040A5442D";
    public static String broadcastMAC = XBee64BitAddress.BROADCAST_ADDRESS.toString();
    // XBee MTU
    public static final int xbeeMTU = 100;

    private DigiMeshDevice xbeeRadio;
    private int xbeePacketLen;
    private byte[] xbeePacketBuffer;
	
	// UI & Map stuff
	private JFrame frmGroundControlStation;
	private JMap map;
	private boolean isMapLoaded;
	private GraphicsLayer graphicsLayer;
	private Symbol uavSymbol;
	private int uav1SymbolId;
	private static final double mapDefaultLat = 39.256206, mapDefaultLong = -76.710170;
	private static final int mapDefaultZoom = 17;
	private static final String IMAGE_QUADCOPTER = "resources/quadcopter-small.png";
	private static final String IMAGE_PLACEHOLDER = "resources/image-placeholder.png";
	// Define the spatial reference of our latitude-longitude points, WGS84
	private static SpatialReference wgs84 = SpatialReference.create(4326);
	private JLabel imgFrame;
	public String thermalImagePalette = "grayscale";

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainWindow window = new MainWindow();
					window.frmGroundControlStation.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public MainWindow() {
		initialize();
	}
	
	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmGroundControlStation = new JFrame();
		frmGroundControlStation.setTitle("Ground Control Station");
		frmGroundControlStation.setBounds(100, 100, 1280, 720);
		frmGroundControlStation.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// Radio setup and connection
		xbeePacketLen = 0;
		xbeePacketBuffer = new byte[1024 * 200];
		Util.resetBuffer(xbeePacketBuffer);
        xbeeRadio = new DigiMeshDevice(PORT, BAUD_RATE, 8, 1, 0, 0);
		try {
            xbeeRadio.open();
            xbeeRadio.addDataListener(this);
            System.out.println("Radio initialized");
            // send alive broadcast that includes system date-time for UAVs to sync their date-time with
            sendGCSMessage(broadcastMAC, new String("SYS_TIME " + (System.currentTimeMillis() / 1000L)));
        } catch (XBeeException e) {
            System.out.println("Can't connect to radio");
            e.printStackTrace();
            //System.exit(1);
        }
		
		// Maps runtime init
		MapsHelper.initLic();
		
		// Map stuff
		
		isMapLoaded = false;
	    // Using MapOptions allows for a common online basemap to be chosen
		// Set initial map position
	    MapOptions mapOptions = new MapOptions(MapType.HYBRID, mapDefaultLat, mapDefaultLong, mapDefaultZoom);
	    // Add a scale bar overlay to the map
	    ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay();
	    // Add a navigator overlay to the map (top-left corner)
	    NavigatorOverlay navigatorOverlay = new NavigatorOverlay();
	    navigatorOverlay.setLocation(LocationOnMap.TOP_LEFT);
	    // Add graphics layer to draw UAVs and other graphics
	    graphicsLayer = new GraphicsLayer();
	    
	    // Create UAV symbol
	    uavSymbol = makeUAVSymbol();
	    GridBagLayout gridBagLayout = new GridBagLayout();
	    gridBagLayout.columnWidths = new int[]{1264, 205, 0};
	    gridBagLayout.rowHeights = new int[]{681, 0};
	    gridBagLayout.columnWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
	    gridBagLayout.rowWeights = new double[]{1.0, Double.MIN_VALUE};
	    frmGroundControlStation.getContentPane().setLayout(gridBagLayout);
	    map = new JMap(mapOptions);
	    // Hide Esri logo
	    map.setShowingEsriLogo(false);
	    map.addMapOverlay(scaleBarOverlay);
	    map.addMapOverlay(navigatorOverlay);
	    map.getLayers().add(graphicsLayer);
	    
	    // Set up map initialization listener
	    map.addMapEventListener(new MapEventListenerAdapter() {
	        @Override
	        public void mapReady(MapEvent event) {
	        	isMapLoaded = true;
	        	// draw UAV1 (just an example)
	        	Point point = (Point)GeometryEngine.project(new Point(mapDefaultLong, mapDefaultLat), wgs84, map.getSpatialReference());
	        	Graphic uav1Graphic = new Graphic(point, uavSymbol);
	        	uav1SymbolId = graphicsLayer.addGraphic(uav1Graphic);
	        }
	      });
	    GridBagConstraints gbc_map = new GridBagConstraints();
	    gbc_map.insets = new Insets(0, 0, 0, 5);
	    gbc_map.fill = GridBagConstraints.BOTH;
	    gbc_map.gridx = 0;
	    gbc_map.gridy = 0;
	    frmGroundControlStation.getContentPane().add(map, gbc_map);
	    
	    JPanel panel = new JPanel();
	    panel.setLayout(null);
	    GridBagConstraints gbc_panel = new GridBagConstraints();
	    gbc_panel.fill = GridBagConstraints.BOTH;
	    gbc_panel.gridx = 1;
	    gbc_panel.gridy = 0;
	    frmGroundControlStation.getContentPane().add(panel, gbc_panel);
	    
	    imgFrame = new JLabel();
	    imgFrame.setLocation(0, 5);
	    imgFrame.setSize(200, 150);
	    ImageIcon img1 = new ImageIcon(IMAGE_PLACEHOLDER);
	    imgFrame.setIcon(img1);
	    panel.add(imgFrame);
	    
	    JButton btnThermalCameraFfc = new JButton("Thermal Camera FFC");
	    btnThermalCameraFfc.setBounds(10, 250, 180, 25);
	    btnThermalCameraFfc.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		sendGCSMessage(uav1MAC, "TCAM_FFC");
	    	}
	    });
	    panel.add(btnThermalCameraFfc);
	    
	    JButton btnStopMessaging = new JButton("Stop Messaging");
	    btnStopMessaging.setBounds(10, 300, 180, 25);
	    btnStopMessaging.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		sendGCSMessage(uav1MAC, "STOP");
	    	}
	    });
	    panel.add(btnStopMessaging);
	    
	    JRadioButton rdbtnColor = new JRadioButton("Color");
	    rdbtnColor.setBounds(10, 162, 80, 23);
	    rdbtnColor.setActionCommand("color");
	    rdbtnColor.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		thermalImagePalette = e.getActionCommand();
	    	}
	    });
	    panel.add(rdbtnColor);
	    
	    JRadioButton rdbtnGrayscale = new JRadioButton("Grayscale");
	    rdbtnGrayscale.setSelected(true);
	    rdbtnGrayscale.setBounds(102, 162, 98, 23);
	    rdbtnGrayscale.setActionCommand("grayscale");
	    rdbtnGrayscale.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		thermalImagePalette = e.getActionCommand();
	    	}
	    });
	    panel.add(rdbtnGrayscale);
	    
	    ButtonGroup thermalImagePaletteButtonGroup = new ButtonGroup();
	    thermalImagePaletteButtonGroup.add(rdbtnColor);
	    thermalImagePaletteButtonGroup.add(rdbtnGrayscale);
		
		// cleanup just before application window is closed.
	    frmGroundControlStation.addWindowListener(new WindowAdapter() {
	      @Override
	      public void windowClosing(WindowEvent windowEvent) {
	        super.windowClosing(windowEvent);
	        try {
	        	// map cleanup
	        	map.dispose();
	        	// close XBee connection
	        	xbeeRadio.close();
	        } catch (Exception exp) {
	        	exp.printStackTrace();
	        }
	      }
	    });
	}
	
	private void sendGCSMessage(String destMAC, String msg) {
		String gcsMessage = "GCS > " + msg; 
		try {
			System.out.println(gcsMessage);
			System.out.println();
			xbeeRadio.sendDataAsync(new XBee64BitAddress(destMAC), gcsMessage.getBytes());
		} catch (XBeeException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void dataReceived(XBeeMessage xbeeMessage) {
		int xbeeMaxPayload = xbeeMTU - 3;
		byte[] data = xbeeMessage.getData();
		int fragmentNumber = Util.bytesToShort(data, 0);
		int bufferLowerBound = (fragmentNumber - 1) * xbeeMaxPayload;
		int bufferUpperBound = bufferLowerBound + data.length - 3;
		//System.out.println("got data" + new String(xbeeMessage.getData()));
		//System.out.println("frameNumber: " + fragmentNumber + ", l: " + bufferLowerBound + ", u: " + bufferUpperBound);
		for (int i = bufferLowerBound, j = 3; i < bufferUpperBound; i++, j++) {
			xbeePacketBuffer[i] = data[j];
		}
		if (data[2] == 1) {
			//System.out.println("received last fragment, fragmentNumber: " + fragmentNumber);
			byte[] msgData = Arrays.copyOf(xbeePacketBuffer, bufferUpperBound);
			Util.resetBuffer(xbeePacketBuffer);
			xbeeMessageReceived(xbeeMessage, msgData);
		}
	}
	
	public void xbeeMessageReceived(XBeeMessage xbeeMessage, byte[] messageData) {
		String address = xbeeMessage.getDevice().get64BitAddress().toString();
		String message = null;
		try {
			message = Util.ungzip(messageData);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
        int rssiLocal = 0;
		try {
			rssiLocal = ByteUtils.byteArrayToInt(xbeeRadio.getParameter("DB"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println(address + "> " + messageData.length + " bytes of data (Local RSSI: -" + rssiLocal + "dBm)");
		
        try {
        	JSONObject jsonMsg = new JSONObject(message);
        	System.out.println("heartbeats: " + jsonMsg.getInt("heartbeats") + ", lastRSSI: " + jsonMsg.getInt("lastRSSI"));
        	JSONObject jsonThermalFrame = jsonMsg.getJSONObject("thermalFrame");
        	System.out.println("frameNumber: " + jsonThermalFrame.getInt("frameNumber") + ", FPA Temperature: " + (jsonThermalFrame.getDouble("fpaTemp") - 273.15) + ", Housing Temperature: " + (jsonThermalFrame.getDouble("housingTemp") - 273.15));
        	// create thermal cam image from received data
        	updateThermalImage(jsonThermalFrame.getString("data"));
		} catch (JSONException e) {
			e.printStackTrace();
		}
        System.out.println();
	}
	
	private void updateThermalImage(String encodedThermalImageData) {
		BufferedImage thermalImage = Util.generateThermalImage(encodedThermalImageData, thermalImagePalette);
		imgFrame.setIcon(new ImageIcon(thermalImage));
	}

	private Symbol makeUAVSymbol() {
		PictureMarkerSymbol symbol = null;
		BufferedImage image = null;

		File uavImageFile = new File(IMAGE_QUADCOPTER);
		try {
			image = ImageIO.read(uavImageFile);
			symbol = new PictureMarkerSymbol(image);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("unable to create picture marker symbol");
			return new SimpleMarkerSymbol(Color.YELLOW, 12, Style.CIRCLE);
		}
		return symbol;
	}
}
