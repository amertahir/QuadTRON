package edu.umbc.cmsc691.updave;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import com.digi.xbee.api.DigiMeshDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.models.XBee64BitAddress;
import com.digi.xbee.api.models.XBeeMessage;
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
import javax.swing.JFrame;

public class MainWindow implements IDataReceiveListener {

	// XBee stuff
	/* Constants */
    // Port where the XBee module is connected to.
    private static final String PORT = "COM6";
    // Baud rate of the XBee module.
    private static final int BAUD_RATE = 115200;
    public static String baseStationMAC = "0013A200408BA39A";
    public static String uav1MAC = "0013A20040A5442D";

    private DigiMeshDevice xbeeRadio;
	
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
	// Define the spatial reference of our latitude-longitude points, WGS84
	private static SpatialReference wgs84 = SpatialReference.create(4326);

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
        xbeeRadio = new DigiMeshDevice(PORT, BAUD_RATE, 8, 1, 0, 0);
		try {
            xbeeRadio.open();
            xbeeRadio.addDataListener(this);
            System.out.println("Radio initialized");
            // send alive broadcast that includes system date-time for UAVs to sync their date-time with
            String gcsAliveMessage = new String("PDAV_GCS > SYS_TIME " + (System.currentTimeMillis() / 1000L));
            System.out.println("Sending alive broadcast message: " + gcsAliveMessage);
            xbeeRadio.sendDataAsync(XBee64BitAddress.BROADCAST_ADDRESS, gcsAliveMessage.getBytes());
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
	    map = new JMap(mapOptions);
	    // Hide Esri logo
	    map.setShowingEsriLogo(false);
	    // Add a scale bar overlay to the map
	    ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay();
	    map.addMapOverlay(scaleBarOverlay);
	    // Add a navigator overlay to the map (top-left corner)
	    NavigatorOverlay navigatorOverlay = new NavigatorOverlay();
	    navigatorOverlay.setLocation(LocationOnMap.TOP_LEFT);
	    map.addMapOverlay(navigatorOverlay);
	    // Add graphics layer to draw UAVs and other graphics
	    graphicsLayer = new GraphicsLayer();
	    map.getLayers().add(graphicsLayer);
	    
	    // Create UAV symbol
	    uavSymbol = makeUAVSymbol();
	    
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
	    
	    // Add the JMap to the JFrame's content pane
	    frmGroundControlStation.getContentPane().add(map);
		
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

	@Override
	public void dataReceived(XBeeMessage xbeeMessage) {
		String address = xbeeMessage.getDevice().get64BitAddress().toString();
        String dataString = xbeeMessage.getDataString();
        System.out.println("Received data from " + address + ": " + dataString);
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
