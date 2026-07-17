RKG_fuelstat.gau
August 01, 2002   version 2.0
by Robert K. Guy

What's New
__________
V2.0 now runs under Windows 98/ME/2000/XP and is designed for FS2002 only.

This version (V2.0) is a drop-in replacement for previous versions of the
gauge - it will not break a panel designed for a previous version.  The gauge
operation is identical to previous versions.  The name of the gauge may
have to be changed if the designer of a panel has changed the name.  It is
usually only necessary to put the gauge in the gauges folder overwriting
any previous version of the gauge.  If the designer of the panel has
customized this gauge by changing any of the bitmaps then the bitmaps
in this version will need to be similarly changed.


This gauge may work with FS2000 and CFS2 but it has not been fully tested.


The Gauge
_________
This is a fuelstatus gauge for FS2002 and Win98/ME/2000/XP. There is another
'famous brand' gauge that is similar but it does not support FS2000/FS2002
type aircraft.  This gauge is entirely original and only visually
resembles that gauge.  This gauge contains no funky code that prevents
you from playing with it. You may rename it or replace or modify the
graphics.  All of the bitmaps used are opaque and the gauges may be
placed on any background bitmap.

This is an FS2002 multi gauge.  It contains 3 basic gauges in a variety
of presentation formats.  There is an airspeed/groundspeed gauge,
a round fuelstatus gauge and a square fuelstatus gauge.  There are
variations of these gauges to support FS98 aircraft, FS2000/FS2002
reciprocating (prop) aircraft and FS2000/FS2002 turbine (jet) aircraft.

All of these gauges share their code and resources - they effectively
become repeaters.  When multiple instances of a gauge are installed in
a panel all of the instances share their settings.  A setting on
one gauge is repeated on the other.

Since this is an FS2002 gauge you may install as many instances of a
gauge as you like.  FS2002 regards this as a single gauge and will
maintain one copy in memory but will display multiple copies.  FS2002
will not make additional copies of the gauge file in order to display
multiple instances.

The airspeed gauge is a stand-alone gauge and is included in some of
the round fuelstatus gauges and some of the square fuelstatus gauges.
The stand-alone airspeed gauge does not repeat with the round and
square gauges.  All round fuelstatus gauges repeat to each other.
All square fuelstatus gauges repeat to each other.  The round gauges
do not repeat to the square gauges.

The round gauges are named 'FuelStatus_' with a suffix indicating
which type aircraft is supported and which visual appearance is
obtained.  The gauges:

  for all FS98 aircraft    description
  ___________________   ___________________
  FuelStatus_98         with a bezel (frame around gauge) with airspeed
  FuelStatus_98_nb      without a bezel (for bitmaps w/gauges built-in)
  FuelStatus_98_nb_ns   no bezel + no airspeed
  FuelStatus_98_ns      no airspeed


  for FS2000/CFS2/FS2002 reciprocating (prop) engine aircraft (like a C182)
  ___________________
  FuelStatus_recip
  FuelStatus_recip_nb
  FuelStatus_recip_nb_ns
  FuelStatus_recip_ns


  for FS2000/CFS2/FS2002 turbine (jet) engine aircraft (and most turboprops)
  ___________________
  FuelStatus_turbine
  FuelStatus_turbine_nb
  FuelStatus_turbine_nb_ns
  FuelStatus_turbine_ns

The square gauges are named 'FuelStatusSq_' with a suffix indicating
which type aircraft is supported and which visual appearance is
obtained.  The gauges:

    for all FS98 aircraft      description
    ___________________        ___________________
    FuelStatusSq_98            the square gauge (with no airspeed)
    FuelStatusSq_98_spd        with an airspeed display below
    FuelStatusSq_98_wide       with an airspeed display to the right


    for FS2000/CFS2/FS2002 reciprocating (prop) engine aircraft (like a C182)
    ___________________
    FuelStatusSq_recip
    FuelStatusSq_recip_spd
    FuelStatusSq_recip_wide


    for FS2000/CFS2/FS2002 turbine (jet) engine aircraft (and most turboprops)
    ___________________
    FuelStatusSq_turbine
    FuelStatusSq_turbine_spd
    FuelStatusSq_turbine_wide


The extraction syntax for this gauge is:

Original size of the airspeed gauge: 176x121
_____________________________________________________
RKG_fuelstat!Airspeed,                  xxx, yyy, www

Original size of the round gauge: 302x302
_____________________________________________________
RKG_fuelstat!FuelStatus_98,             xxx, yyy, www
RKG_fuelstat!FuelStatus_98_nb,          xxx, yyy, www
RKG_fuelstat!FuelStatus_98_nb_ns,       xxx, yyy, www
RKG_fuelstat!FuelStatus_98_ns,          xxx, yyy, www
RKG_fuelstat!FuelStatus_recip,          xxx, yyy, www
RKG_fuelstat!FuelStatus_recip_nb,       xxx, yyy, www
RKG_fuelstat!FuelStatus_recip_nb_ns,    xxx, yyy, www
RKG_fuelstat!FuelStatus_recip_ns,       xxx, yyy, www
RKG_fuelstat!FuelStatus_turbine,        xxx, yyy, www
RKG_fuelstat!FuelStatus_turbine_nb,     xxx, yyy, www
RKG_fuelstat!FuelStatus_turbine_nb_ns,  xxx, yyy, www
RKG_fuelstat!FuelStatus_turbine_ns,     xxx, yyy, www

Original size of the square gauge: 236x127
I use this gauge with Copilot from Abacus in a separate panel.
_____________________________________________________
RKG_fuelstat!FuelStatusSq_98,           xxx, yyy, www
RKG_fuelstat!FuelStatusSq_recip,        xxx, yyy, www
RKG_fuelstat!FuelStatusSq_turbine,      xxx, yyy, www

Original size of the square with airspeed gauge: 236x183
_____________________________________________________
RKG_fuelstat!FuelStatusSq_98_spd,       xxx, yyy, www
RKG_fuelstat!FuelStatusSq_recip_spd,    xxx, yyy, www
RKG_fuelstat!FuelStatusSq_turbine_spd,  xxx, yyy, www

Original size of the wide with airspeed gauge: 400x127
Sized to be used in a separate panel with CoPilot from Abacus.
_____________________________________________________
RKG_fuelstat!FuelStatusSq_98_wide,      xxx, yyy, www
RKG_fuelstat!FuelStatusSq_recip_wide,   xxx, yyy, www
RKG_fuelstat!FuelStatusSq_turbine_wide, xxx, yyy, www



AN EXAMPLE
__________
This is what I use in my C182 panel.  Included in this archive is
the bitmap needed for this.  Your window number may not be 05!

[Window05]
file=copilot_fs.bmp
size_mm=400
windowsize_ratio=0.35
position=2
visible=0
ident=ANNUNCIATOR2_PANEL

gauge00=CoPilot,  0,0,400
gauge01=RKG_fuelstat!FuelStatusSq_recip,  166,166,236



OPERATION OF THE GAUGE
______________________
AIRSPEED/GROUNDSPEED
The airspeed gauge will change its' units of display when left-clicked.
The display is initially Knots and changes to MPH then KPH then back
to Knots on each click.  This applies to the stand-alone gauge and the
airspeed gauge that appears in some of the fuelstatus gauges.
The airspeed value is TAS or IAS and dependent on the realism settings.
The groundspeed value is true groundspeed in the forward direction.
The groundspeed value is not reliable while on the ground.

FUELSTATUS
The gauge has on the left top a low-fuel indicator that indicates the
Microsoft FS2000 low-fuel status.  It flashes red then turns solid red
driven by the built-in FS2000 code.  I haven't been able to confirm
what MS thinks is a low-fuel condition.  It seems to be arbitrary.

On the top left is a units indicator light.  You can left click it to
change the units used in the display.  When green it uses US/English
units and when yellow it uses Metric units.

On the bottom of the gauge are the following indicators/buttons:
FLOW  : fuelflow
REM   : remaining fuel
USED  : fuel used so far
TtoE  : time to empty in hours:mins
RNG   : range (until empty)

Units used:
GPH - gallons per hour
PPH - pounds per hour
LPH - liters per hour
KgH - kilograms per hour
GAL - gallons
LBS - pounds
L   - liters
Kg  - kilograms
KN  - nautical miles
MI  - US miles
Knt - Knots
MPH - miles per hour
KM  - kilometers
KPH - kilometers per hour

Left clicking a button lights it up and indicates what is being
displayed on the gauge.  Some of the buttons can be clicked again to
change what is being displayed.  Click again to change the color of
the indicator and the display changes as follows:


           units=green (US)             units=yellow (METRIC)
         ___________________            ____________________
button   green        yellow            green         yellow
     ____________________________________________________________
FLOW      GPH          PPH               LPH           KgPH

REM       GAL          LBS                L             Kg

USED      GAL          LBS                L             Kg

NOTE: You can right click the USED button to reset used fuel to 0.
      You could use this feature to measure fuel use over a flight
      segment.  The fuel used calculation takes into account the
      addition of fuel.  If you use the Aircraft|Fuel menu to add fuel
      (or any other method) the gauge will continue to add to
      fuel used.  If you land and refuel, for example, the fuel used
      quantity will not be reset - it will continue to add up resulting
      in a fuel used value greater than the total fuel capacity of the
      aircraft.  If you change aircraft inside the simulation to an
      aircraft containing this gauge in a panel, the gauge is freshly
      installed and the fuel used is set to 0.


TtoE   Time to empty does not change when clicked.


RANGE: The distance displayed is range until empty based on current
       speed and fuel consumption.  No assumption is made in
       consideration of lower fuel use during descent, approach and
       landing.  The groundspeed values are true groundspeed in the
       forward direction.  The groundspeed values are not reliable
       while on the ground.

      units=green (US)
      green        yellow       blue                  red
    ________________________________________________________________
RNG    KN            MI         Knt (groundspeed)     MPH (groundspeed)


      units=yellow (METRIC)
      green        yellow       blue                  red
    ________________________________________________________________
RNG    KN            KM         Knt (groundspeed)     KPH (groundspeed)


NOTE: When the low-fuel light is red, when FS2002 signals a low-fuel
      state, the color of the display will change to orange.
      REM (remaining fuel), TtoE (time to empty) and RNG (range) will
      change color.


TO INSTALL
__________
Unzip the file, RKG_fuelstat.gau, into your FS2002 gauges folder.
OR...
Unzip the zip file anywhere and move RKG_fuelstat.gau to your FS2002
gauges folder.

If you want to use the included bitmap, I'm assuming you know how to
create a panel and edit it and you'll know what to do with the bitmap.
Put the bitmap into the folder containing the panel.cfg or put it into
any folder and use a relative or absolute address. See also the example above.
  Ex:
  inside the panel's folder ......... file=copilot_fs.bmp
  inside \aircraft\copilot .......... file=..\..\copilot\copilot_fs.bmp


A NOTE
______

This gauge was tested with WinXP with service pack 1 and Q306676 hot-fix
from Microsoft which includes a new D3D8.dll for Windows DirectDraw3D.
The Q306676 may not be required for all installations and fixes some Video
card related problems.  The Q306676 hot-fix is available from the WinXP
update site at Microsoft.com.  This gauge was tested with NVidia geForce2
and geForce4 video cards.

This gauge may seem large but 531Kb is the bitmaps.  The code
that runs the gauges is small and fairly fast.  The bitmaps you don't
use aren't kept in memory, only the code is permanently resident.  The
code used by FS2002 to operate the gauge is about 12Kb in size.  I have
done nothing slick in the code - it does use FSUIPC but does not require any
special handling.  If the gauge seems not to respond, for example
displaying 0 fuel flow, remember that aircraft can be of three
varieties: FS98 imported aircraft, FS2000/FS2002 prop aircraft and
FS2000/FS2002 turbine aircraft.  Many available aircraft advertise that
they are FS2000/FS2002 aircraft when they are really made over FS98 aircraft.
Some turboprop aircraft and helicopters are reciprocating engine in
their .air files and some are turbine engine.  I have yet to find an
aircraft that can't use this gauge.

If you believe this gauge is causing your FS2002 to fail
just stop using it.  This gauge can not damage your FS2002 files.

The behavior of the previous version (V1.0) of this gauge has been fixed.
When the V1.0 gauge was run under WinXP and FS2002 it would cause FS2002 to
abruptly close with no warning or error message.  This was apparently a
WinXP problem possibly related to the inclusion of debugging info and
exception handling code in WinXP.  This gauge now includes exception handling
code which makes it somewhat larger and unnecessarily complicated but does
not seem to slow down FS2002.



Copyright 2002 Robert K. Guy.
You may not make money with this gauge in any way whatsoever but you
may freely distribute this gauge with any panel you design that is
exclusively freeware.  You are also free to upload this gauge to any
other FS site that does not charge a fee for access or for downloads.

This is Freeware.

Bob Guy
zbobg@juno.com
