"""Team API contract. Keep the required JSON alias `data`."""
from typing import Literal, Optional
from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt


class TrafficReading(BaseModel):
    model_config = ConfigDict(extra='forbid')
    light_detected: StrictBool
    light_color: Optional[Literal['red', 'green']]
    seconds_remaining: Optional[StrictInt] = Field(default=None, ge=0, le=99)
    is_safe_to_cross: StrictBool


class TrafficResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra='forbid')
    status: Literal['success', 'error']
    message: str
    traffic_reading: Optional[TrafficReading] = Field(alias='data')


def build_success(light_color=None, seconds_remaining=None):
    if light_color not in ('red', 'green'):
        light_color = None
    if type(seconds_remaining) is not int or not 0 <= seconds_remaining <= 99:
        seconds_remaining = None
    if light_color != 'green':
        seconds_remaining = None
    return TrafficResponse(
        status='success',
        message='紅綠燈辨識完成' if light_color else '未偵測到紅綠燈',
        traffic_reading=TrafficReading(
            light_detected=light_color is not None,
            light_color=light_color,
            seconds_remaining=seconds_remaining,
            is_safe_to_cross=(light_color == 'green' and seconds_remaining is not None and seconds_remaining >= 15),
        ),
    )


def build_error(message):
    return TrafficResponse(status='error', message=message, traffic_reading=None)
