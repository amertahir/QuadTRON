#ifndef LEPTON_I2C
#define LEPTON_I2C

#include "leptonSDKEmb32PUB/LEPTON_SDK.h"
#include "leptonSDKEmb32PUB/LEPTON_SYS.h"
#include "leptonSDKEmb32PUB/LEPTON_Types.h"

int lepcom_connect();
void lepcom_disconnect();
void lepcom_disable_auto_ffc();
void lepcom_perform_ffc();
int lepcom_enable_telemetry(int header);
LEP_SYS_FFC_SHUTTER_MODE_OBJ_T lepcom_get_ffc_shutter_mode();
LEP_SYS_SHUTTER_POSITION_E lepcom_toggle_shutter();
LEP_SYS_FPA_TEMPERATURE_CELCIUS_T lepcom_get_aux_temperature();
LEP_SYS_FPA_TEMPERATURE_CELCIUS_T lepcom_get_fpa_temperature();

#endif
