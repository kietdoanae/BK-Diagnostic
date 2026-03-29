/**
 * @file    obd2_pids.h
 * @brief   OBD2 Standard PIDs (Mode 01) + Ford-specific PIDs (Mode 22)
 *
 * Reference:
 *  - SAE J1979 / ISO 15031-5  (OBD2 standard modes)
 *  - Ford Workshop Manual PIDs (Mode 22, Ford-specific)
 */

#ifndef OBD2_PIDS_H
#define OBD2_PIDS_H

#include <stdint.h>

/* ── OBD2 Service Modes ──────────────────────────────────────────────────── */
#define OBD2_MODE_CURRENT_DATA    0x01  /* Show current data                */
#define OBD2_MODE_FREEZE_FRAME    0x02  /* Show freeze frame data           */
#define OBD2_MODE_STORED_DTC      0x03  /* Show stored DTCs                 */
#define OBD2_MODE_CLEAR_DTC       0x04  /* Clear DTCs and stored values     */
#define OBD2_MODE_FORD_SPECIFIC   0x22  /* Ford manufacturer-specific       */

/* ── Standard OBD2 PIDs (Mode 01) ───────────────────────────────────────── */
typedef enum {
    PID_SUPPORTED_01_20        = 0x00, /* PIDs supported [01-20]           */
    PID_MIL_STATUS             = 0x01, /* Monitor status since DTCs cleared */
    PID_ENGINE_COOLANT_TEMP    = 0x05, /* °C = A - 40                      */
    PID_SHORT_FUEL_TRIM_1      = 0x06, /* % = (A-128)*100/128              */
    PID_LONG_FUEL_TRIM_1       = 0x07, /* % = (A-128)*100/128              */
    PID_ENGINE_RPM             = 0x0C, /* RPM = ((A*256)+B)/4              */
    PID_VEHICLE_SPEED          = 0x0D, /* km/h = A                         */
    PID_TIMING_ADVANCE         = 0x0E, /* °BTDC = A/2 - 64                 */
    PID_INTAKE_AIR_TEMP        = 0x0F, /* °C = A - 40                      */
    PID_MAF_RATE               = 0x10, /* g/s = ((A*256)+B)/100            */
    PID_THROTTLE_POSITION      = 0x11, /* % = A*100/255                    */
    PID_O2_SENSOR_B1S1         = 0x14, /* mV = A*5/1000 (approx)           */
    PID_ENGINE_LOAD            = 0x04, /* % = A*100/255                    */
    PID_FUEL_PRESSURE          = 0x0A, /* kPa = A*3                        */
    PID_INTAKE_MANIFOLD_PRESS  = 0x0B, /* kPa = A                          */
    PID_RUN_TIME               = 0x1F, /* seconds = (A*256)+B              */
    PID_DISTANCE_WITH_MIL      = 0x21, /* km = (A*256)+B                   */
    PID_FUEL_RAIL_PRESSURE     = 0x23, /* kPa = ((A*256)+B)*10             */
    PID_COMMANDED_EGR          = 0x2C, /* % = A*100/255                    */
    PID_BAROMETRIC_PRESSURE    = 0x33, /* kPa = A                          */
    PID_CONTROL_MODULE_VOLTAGE = 0x42, /* V = ((A*256)+B)/1000             */
    PID_ABSOLUTE_LOAD          = 0x43, /* % = ((A*256)+B)*100/255          */
    PID_AMBIENT_AIR_TEMP       = 0x46, /* °C = A - 40                      */
    PID_FUEL_TYPE              = 0x51, /* Fuel type encoding               */
    PID_ETHANOL_PERCENT        = 0x52, /* % = A*100/255                    */
} OBD2_PID_t;

/* ── Ford-specific PIDs (Mode 22) ────────────────────────────────────────── */
typedef enum {
    FORD_PID_TRANS_OIL_TEMP    = 0x1156, /* Transmission oil temp (°C)     */
    FORD_PID_OIL_TEMP          = 0x115C, /* Engine oil temp (°C)           */
    FORD_PID_OIL_PRESSURE      = 0x115D, /* Engine oil pressure (kPa)      */
    FORD_PID_BOOST_PRESSURE    = 0x115E, /* Turbo boost pressure (kPa)     */
    FORD_PID_TRANS_GEAR        = 0x118C, /* Current gear position          */
    FORD_PID_TORQUE_ACTUAL     = 0x1090, /* Engine actual torque (Nm)      */
    FORD_PID_TORQUE_MAX        = 0x1091, /* Engine max torque (Nm)         */
    FORD_PID_CLUTCH_STATUS     = 0x1159, /* Clutch pedal status            */
    FORD_PID_FUEL_LEVEL        = 0x2F00, /* Fuel level % (Ford ext)        */
    FORD_PID_BATTERY_SOC       = 0x2900, /* HV battery SOC (Ford hybrid)   */
} Ford_PID_t;

/* ── PID descriptor for Android layer ───────────────────────────────────── */
typedef struct {
    uint8_t  mode;
    uint16_t pid;       /* 1-byte for Mode 01, 2-byte for Mode 22 */
    uint8_t  resp_len;  /* expected response data bytes           */
    float    scale;
    float    offset;
    char     unit[8];
    char     name[24];
} PID_Descriptor_t;

/* ── OBD2 request payload (sent from Android, received by STM32) ─────────
 *  payload[0] = mode (0x01 or 0x22)
 *  payload[1] = PID high byte  (0x00 for Mode 01)
 *  payload[2] = PID low byte
 */
typedef struct {
    uint8_t  mode;
    uint16_t pid;
} PID_Request_t;

/* ── OBD2 response payload (sent from STM32 to Android) ─────────────────
 *  payload[0] = mode
 *  payload[1] = PID high
 *  payload[2] = PID low
 *  payload[3..6] = raw A,B,C,D bytes (4 bytes always, pad 0xFF if unused)
 */
typedef struct {
    uint8_t  mode;
    uint16_t pid;
    uint8_t  raw[4];   /* raw ECU response bytes A,B,C,D */
} PID_Response_t;

/* ── Well-known OBD2 PID table (for reference / Android layer) ──────────── */
static const PID_Descriptor_t OBD2_PID_TABLE[] = {
    { OBD2_MODE_CURRENT_DATA, PID_ENGINE_RPM,          2, 0.25f,    0.f,   "RPM",  "Engine RPM"            },
    { OBD2_MODE_CURRENT_DATA, PID_VEHICLE_SPEED,       1, 1.0f,     0.f,   "km/h", "Vehicle Speed"         },
    { OBD2_MODE_CURRENT_DATA, PID_ENGINE_COOLANT_TEMP, 1, 1.0f,   -40.f,   "°C",   "Coolant Temp"          },
    { OBD2_MODE_CURRENT_DATA, PID_INTAKE_AIR_TEMP,     1, 1.0f,   -40.f,   "°C",   "Intake Air Temp"       },
    { OBD2_MODE_CURRENT_DATA, PID_THROTTLE_POSITION,   1, 0.3922f,  0.f,   "%",    "Throttle Position"     },
    { OBD2_MODE_CURRENT_DATA, PID_ENGINE_LOAD,         1, 0.3922f,  0.f,   "%",    "Engine Load"           },
    { OBD2_MODE_CURRENT_DATA, PID_MAF_RATE,            2, 0.01f,    0.f,   "g/s",  "MAF Air Flow"          },
    { OBD2_MODE_CURRENT_DATA, PID_SHORT_FUEL_TRIM_1,   1, 0.7813f,-100.f,  "%",    "Short Fuel Trim B1"    },
    { OBD2_MODE_CURRENT_DATA, PID_LONG_FUEL_TRIM_1,    1, 0.7813f,-100.f,  "%",    "Long Fuel Trim B1"     },
    { OBD2_MODE_CURRENT_DATA, PID_TIMING_ADVANCE,      1, 0.5f,   -64.f,   "°",    "Timing Advance"        },
    { OBD2_MODE_CURRENT_DATA, PID_BAROMETRIC_PRESSURE, 1, 1.0f,     0.f,   "kPa",  "Barometric Pressure"   },
    { OBD2_MODE_CURRENT_DATA, PID_CONTROL_MODULE_VOLTAGE, 2, 0.001f, 0.f,  "V",    "Module Voltage"        },
    { OBD2_MODE_FORD_SPECIFIC, FORD_PID_OIL_TEMP,      2, 0.1f,   -40.f,   "°C",   "Engine Oil Temp"       },
    { OBD2_MODE_FORD_SPECIFIC, FORD_PID_TRANS_OIL_TEMP,2, 0.1f,   -40.f,   "°C",   "Trans Oil Temp"        },
    { OBD2_MODE_FORD_SPECIFIC, FORD_PID_BOOST_PRESSURE,2, 0.1f,     0.f,   "kPa",  "Boost Pressure"        },
    { OBD2_MODE_FORD_SPECIFIC, FORD_PID_TORQUE_ACTUAL, 2, 1.0f,     0.f,   "Nm",   "Engine Torque"         },
};

#define OBD2_PID_TABLE_SIZE  (sizeof(OBD2_PID_TABLE) / sizeof(OBD2_PID_TABLE[0]))

#endif /* OBD2_PIDS_H */
