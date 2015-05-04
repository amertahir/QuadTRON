#ifndef LIB_LEPTON
#define LIB_LEPTON

#include <ctime>
#include <stdint.h>
#include "lepton_i2c.h"

#define PACKET_SIZE 164
#define PACKET_SIZE_UINT16 (PACKET_SIZE/2)
#define PACKETS_PER_FRAME_IMAGE 60
#define PACKETS_PER_FRAME_TELEMETRY 3
#define PACKETS_PER_FRAME (PACKETS_PER_FRAME_IMAGE+PACKETS_PER_FRAME_TELEMETRY)
#define FRAME_SIZE_IMAGE_UINT16 (PACKET_SIZE_UINT16*PACKETS_PER_FRAME_IMAGE)
#define IMAGE_WIDTH 80
#define IMAGE_HEIGHT 60

typedef struct TELEMETRY_INFO_t {
	uint32_t frame_counter;
	float fpa_temp;
	float housing_temp;
} TELEMETRY_INFO;

uint8_t image_buf[PACKET_SIZE*PACKETS_PER_FRAME_IMAGE];
uint16_t telemetry_buf[PACKET_SIZE_UINT16];
TELEMETRY_INFO telemetry;
void parse_telemetry_packet(uint16_t *packet);

extern "C" {
	void lepton_connect();
	void lepton_disconnect();
	void lepton_perform_ffc();
	void lepton_get_frame(uint16_t *image_data, long &frame_counter, float &fpa_temp, float &housing_temp);
}

#endif
