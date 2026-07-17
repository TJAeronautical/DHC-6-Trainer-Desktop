IVSI/TCAS With 767PIC Interface
version 1.1.16
30 July 2003

Lee Hetherington
ilh@sls.lcs.mit.edu



REQUIREMENTS BEFORE YOU START

This gauge requires FSUIPC for the source of TCAS traffic
information. Without it this addon will not work properly. Get it
here: http://www.schiratti.com/dowson.html.  Note that this gauge
contains a license key enabling it to work with an unregistered copy
of FSUIPC 3.x.

Note: although 767PIC does not currently work in FS2004, this gauge
does.
___________________________________________________________________________

Traffic is displayed with one of four symbols, a two-digit relative
altitude, and an arrow indicating a vertical rate of at least +/-
300fpm.  The altitude is indicated in units of 100ft, with a +
indicating above ourself, and a - indicating below.

A red square indicates a Threat and would be accompanied by an aural
warning and a commanded vertical speed along the VSI scale for a
Resolution Advisory (RA).  Neither the aural warning or vertical
command is implemented at this time.  I hope to have a future version
that implements both aural warnings and vertical speed advisories.

A yellow circle indicates a Potential Threat and would be accompanied
by the aural warning "Traffic, Traffic" for a Traffic Advisory (TA).
A TA generally precedes an RA by 15-20 seconds.

A blue or white solid diamond indicates Proximate Traffic that is
within 6nm and +/- 1200ft vertically and is not an RA or TA.

A blue or white hollow diamond indicates Other Traffic that is within
a settable vertical range of own aircraft.  See the option "other"
below for this setting, and the setting of ABOVE/N/BELOW on the
transponder gauge for exceptions to this range.  Note that for the
duration of an RA or TA all Other Traffic is removed from the screen
so as to remove clutter during a potentially critical encounter.

The blue/white color for Proximate and Other Traffic is settable via
gauge options, as described below.

Note that Potential Threat and Threat traffic that would otherwise be
offscreen is drawn right at the edge so it can be seen.  In this case,
the range is incorrectly displayed, but the bearing is correct.

Also note that the traffic is displayed with heading and not track up.


USAGE

The IVSI gauge has four hidden click spots that operate as follows:

  o upper left: cycles the transponder mode between TEST, (STBY),
    (XPDR), TA, and RA;

  o upper right: cycles the TCAS range between 6, 12, 18, 24, and
    40nm;

  o lower left: cycles TCAS vertical range between (BELOW), (N),
    (ABOVE); and

  o lower right: cycles the TCAS altitude display between (REL) and
    (ABS).

Here, labels put in parentheses are not displayed on the IVSI itself,
but they are displayed on the transponder panel.  All of the above can
also be controlled by clickspots on the transponder panel.  In
addition, you can control the squawk code.

NOTE: all click spots on both the IVSI and transponder are sensitive
to both LEFT and RIGHT mouse clicks.  A LEFT click reduces a setting,
and a RIGHT click increases it.

The modes are as follows:

  o TEST: a test pattern with all four intruder types displayed.  The
    range is displayed as 6nm, regardless of the TCAS range selector.

  o STBY: transponder is in standby (not squawking), and TCAS is
    disabled.

  o XPDR: transponder is active, but TCAS is disabled.

  o TA: transponder is in TA ONLY mode, displaying other, proximate,
    and potential threat traffic, but no threats will be identified.

  o RA: transponder is in TA/RA mode, the normal TCAS mode, displaying
    all types of intruders.  If RA processing were fully implemented,
    threats would generally result in vertical speed commands on the
    IVSI.

For all ranges, a 2nm range circle is displayed around the aircraft.
For selected ranges greater than 6nm, a 5nm range ring is added.

The ABOVE/N/BELOW selection can be used to limit how much traffic is
visible by limiting the vertical range of other traffic.  By default,
selecting N will only show traffic in the relative flight level range
-27 to +27.  With ABOVE, the range is -27 to +99.  With BELOW, the
range is -99 to +27.  (The value 27 is settable via the gauge parameter
"other" on the IVSI gauge line in the Panel.cfg.)

Finally, the ABS/REL selection controls how altitudes are displayed
for TCAS intruders.  With ABS, absolute flight levels are displayed.
With REL, relative flight levels are displayed.

You should make sure your FSUIPC settings have TCAS enabled,
preferably to 40nm range so that the gauge's 40nm range is fully
utilized.  If you have TCAS turned off in FSUIPC's settings (i.e.,
range set to 0nm), you will not see any TCAS targets.

If you see an amber TCAS "flag" and no plane or range rings, then a
usable to connection to FSUIPC could not be established.  This could
mean that FSUIPC is not properly installed, or that the licensing for
this gauge is no longer valid.  TCAS will be functional, but the gauge
will still display V/S.

============================================================

MULTIPLAYER OR SQUAWKBOX USE

You will need the program AIBridge, available in the AVSIM file
library, in order to see other aircraft.  AIBridge injects multiplayer
traffic into FSUIPC's traffic tables.  Otherwise, only AI traffic seen
by FSUIPC and thus this IVSI/TCAS gauge.

Within SquawkBox, the multiplayer range setting will limit the range
of visible TCAS traffic.

I have heard that SB3, currently under development, will inject
traffic directly into FSUIPC without the need for AIBridge.


GAUGE OPTIONS

The following options can be specified in the panel.cfg file for the
ILH_TCAS!IVSI gauge (Main Panel, [Window01] section, after the final
comma "," on the "gauge14" line as mentioned above):

     fontscale:<N>           Scale the TCAS altitude font by <N> (e.g.,
                             0.8 or 0.9).  You may want a value
                             smaller than 1.0 depending on your
                             eyesight and the resolution at which you
                             run FS.

     other:<N>               Limit Other Traffic to +/- <N> hundred
                             feet.  The default is 27.  Some carriers
                             might use 45.  Setting this to 99 will
                             show all possible traffic regardless of
                             the setting of ABOVE/N/BELOW.

     blueplane:<yes|no>      The TCAS self plane and distance scales
                             will be blue if "yes", otherwise white.
                             The Other and Proximate Traffic will be
                             the opposite color of the self plane.
                             Think of this as a carrier option and
                             defaults to "no".

     vsirate:<N>             The target rate in redraws per second for
                             the VSI needle.  The default of 18 means
                             once per 1/18th second FS tick, and 0
                             means for every FS frame, which in my
                             opinion would be overkill.

     aa:<yes|no>             Whether antialiasing should be used within
                             the gauge.  Defaults to "yes".  You could
                             set it to "no" to see the effect of
                             antialiasing.  I do not support the "no"
                             option as it really offers no advantages.


     pic:<yes|no>            Whether the gauges should respond to 767PIC
                             internal state such as panel lighting,
                             power status, IRU status, etc.  You will
                             want to use no if you are using the
                             gauge(s) outside of 767PIC.  This option
                             also works on the ILH_TCAS!Transponder
                             gauge.

The following is an example of changing the "fontscale" and the
"other" options:

        gauge14=ILH_TCAS!IVSI, 545, 391, 158, 158, other:45 fontscale:0.8



USING THE GAUGES OUTSIDE OF 767PIC

There are so-called "named variables" that can be used to control the
IVSI, and the transponder is linked to the IVSI through them.  Email
me if you are a panel designer and wish to control these gauges.


KNOWN ISSUES

Some versions of FSUIPC before 2.973 are known to cause blinking of
TCAS targets when used with AIBridge traffic.

This gauge was developed to work with 767PIC patches 1.2 and 1.3 for
FS2002.  Various offsets into APS.dll are used to read the state of
panel flood lighting, left main bus available, and the left IRU
alignment status.  For other versions of APS.dll, the 767PIC specifics
will be inoperable.


LEGAL

This IVSI/TCAS gauge(s) is a freeware product and a labor of love and
may NOT be included in any commercial package or website without
permission. The IVSI/TCAS gauge(s) is an add-on to Wilco's 767PIC and
is not endorsed, part of, or related to Wilco Publishing.

If you decide to use any part of this package, whether it is for
freeware use or commercial, you MUST obtain prior permission from me,
the author.  Should there be any damage done to your system after using
this package, I and all parties involved will not be held responsible.


ACKNOWLEDGEMENTS

Nick Jacobs of Dreamfleet gave me several pointers on general gauge
programming that proved invaluable.  Mark McGrath, Ryan O'Malley, and
John Selph provided me with TCAS documentation that was very helpful
in developing my threat classification and display code.  Claude
Troncy provided guidance on a technique to read the main panel
lighting status through an offset into APS.dll for the 1.2 patch.  I
have since figured out many other offsets for the 1.2 and 1.3 patches.

Mark McGrath provided the motivation for me to implement the new
transponder, and Ian Riddel provided a photo that served as the
starting point for the background bitmap.

Finally, the beta testers Michael Bevington, Robert Hall, Mark
McGrath, John Selph, and Ryan O'Malley, all provided valuable feedback
on the first version of this gauge.  Beta testers Michael Bevington,
Robert Hall, Mark McGrath, Bill Van Caulart, and Ian Riddel helped
extensively with testing this version and its documentation.  Thanks!
