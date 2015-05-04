#include "liblepton.h"

#include "SPI.h"
#include "LEPTON_Macros.h"
#include "lepton_i2c.h"
#include "gpio.h"

#define PACKET_SIZE 164
#define PACKET_SIZE_UINT16 (PACKET_SIZE/2)
#define PACKETS_PER_FRAME_IMAGE 60
#define PACKETS_PER_FRAME_TELEMETRY 3
#define PACKETS_PER_FRAME (PACKETS_PER_FRAME_IMAGE+PACKETS_PER_FRAME_TELEMETRY)
#define FRAME_SIZE_IMAGE_UINT16 (PACKET_SIZE_UINT16*PACKETS_PER_FRAME_IMAGE)
#define IMAGE_WIDTH 80
#define IMAGE_HEIGHT 60


void lepton_connect() {
	// red dot issue fix => sync camera by toggling CS pin
	gpio_state(8, 1);
	usleep(200000); // wait > 185ms to resync
	gpio_state(8, 0);

	// connect I2C
	lepcom_connect();

	// disable auto-FFC
	lepcom_disable_auto_ffc();

	// enable telemetry (footer, i.e. packets 61-63)
	lepcom_enable_telemetry(1);

	//open spi port
	SpiOpenPort(0);
}

void lepton_disconnect() {
	SpiClosePort(0);
	lepcom_disconnect();
}

void lepton_perform_ffc() {
	// perform FFC
	lepcom_perform_ffc();
}

void lepton_get_frame(uint16_t *image_data, long &frame_counter, float &fpa_temp, float &housing_temp)
{
	int i = 0, row = 0, column = 0, temp = 0;
	int resets = 0;
	int packetNumber = 0;

	// read data packets from lepton over SPI
	resets = 0;
	packetNumber = 0;
	for (i = 0; i < PACKETS_PER_FRAME; i++) {
		// if it's a drop packet, reset i to 0 -> set to -1 so it will be at 0 in the beginning of the iteration
		if (i < PACKETS_PER_FRAME_IMAGE) {
			read(spi_cs0_fd, image_buf+sizeof(uint8_t)*PACKET_SIZE*i, sizeof(uint8_t)*PACKET_SIZE);
			packetNumber = image_buf[i*PACKET_SIZE+1];
		} else {
			read(spi_cs0_fd, telemetry_buf, sizeof(uint16_t)*PACKET_SIZE_UINT16);
			if (i == PACKETS_PER_FRAME_IMAGE) {
				parse_telemetry_packet(telemetry_buf);
				fpa_temp = telemetry.fpa_temp;
				housing_temp = telemetry.housing_temp;
				frame_counter = telemetry.frame_counter;
			}
			packetNumber = REVERSE_ENDIENESS_UINT16(telemetry_buf[0]);
		}
		if (packetNumber != i) {
			i = -1;
			resets += 1;
			usleep(1000);
			// Note: we've selected 750 resets as an arbitrary limit, since there should never be 750 "null" packets between two valid transmissions at the current poll rate
			// By polling faster, developers may easily exceed this count, and the down period between frames may then be flagged as a loss of sync
			if (resets == 750) {
				SpiClosePort(0);
				usleep(750000);
				SpiOpenPort(0);
			}
		}
	}

	for(i = 0; i < FRAME_SIZE_IMAGE_UINT16; i++) {
		// skip the first 2 uint16_t's of every packet, they're 4 header bytes
		if (i % PACKET_SIZE_UINT16 < 2) {
			continue;
		}

		// flip the MSB and LSB at the last second
		temp = image_buf[i*2];
		image_buf[i*2] = image_buf[i*2+1];
		image_buf[i*2+1] = temp;
		
		column = (i % PACKET_SIZE_UINT16) - 2;
		row = i / PACKET_SIZE_UINT16;
		image_data[(row * IMAGE_WIDTH) + column] = ((uint16_t *)image_buf)[i];
	}
}

void parse_telemetry_packet(uint16_t *packet) {
	// read 32bit unsigned int frame counter and reverse bytes
	telemetry.frame_counter = REVERSE_ENDIENESS_UINT16(packet[23])  << 16 | REVERSE_ENDIENESS_UINT16(packet[22]);
	// read 16bit FPA and Housing temperatures
	//uint16_t ft = packet[52] << 8 | packet[53];
	//uint16_t ht = packet[56] << 8 | packet[57];
	uint16_t ft = REVERSE_ENDIENESS_UINT16(packet[26]);
	uint16_t ht = REVERSE_ENDIENESS_UINT16(packet[28]);
	// now convert from kelvinx100 to kelvin
	telemetry.fpa_temp = (float)( ( ft / 100 ) + ( ( ft % 100 ) * .01 ) );
	telemetry.housing_temp = (float)( ( ht / 100 ) + ( ( ht % 100 ) * .01 ) );
	//telemetry.fpa_temp = (float)( ( ( ft / 100 ) + ( ( ft % 100 ) * .01 ) ) - 273.15 );
	//telemetry.housing_temp = (float)( ( ( ht / 100 ) + ( ( ht % 100 ) * .01 ) ) - 273.15 );
}
