### Permissions:





Command permissions:
- `norestone.cmd.sim`: "/sim"
- `norestone.cmd.sim.select`: "/sim desel", "/sim pos1", "/sim 1", "/sim pos2", "/sim 2"
- `norestone.cmd.sim.freeze`: "/sim freeze"
- `norestone.cmd.sim.step`: "/sim step"
- `norestone.cmd.sim.compile`: "/sim compile", "/sim clear"
- `norestone.cmd.sim.compile.backendaccess.<backendId>`: "/sim compile <backendId>"

Disclaimer: command permissions are separate from the underlying mechanism they allow.
Example: granting access to "/sim compile" does not necessarily allow you to compile, it only
allows you to run the command.


General permissions:
- `norestone.simulation.selection.select`: Base ability to select (=> in plots that are trusted or you're the owner of)
- `norestone.simulation.selection.select.bypass`: Ability to select everywhere
- `norestone.simulation.compile`: Ability to compile/clear

Number permissions:
- `norestone.simulation.maxtps.<int>`:
- `norestone.simulation.maxtps.bypass`:
- `norestone.simulation.maxdims.x.<int>`:
- `norestone.simulation.maxdims.y.<int>`:
- `norestone.simulation.maxdims.z.<int>`:
- `norestone.simulation.maxdims.bypass`:
- `norestone.simulation.maxvolume.<long>`:
- `norestone.simulation.maxvolume.bypass`:
