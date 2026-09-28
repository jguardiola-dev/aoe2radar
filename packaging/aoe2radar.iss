; aoe2radar — instalador por usuario (Inno Setup 6). Lo compila el perfil «instalador» del pom
; (mvn -Pempaquetar,instalador -DskipTests verify) a partir del app-image de jpackage (target/dist/aoe2radar).
;
; - Por usuario, sin administrador: %LOCALAPPDATA%\Programs\aoe2radar ({userpf} con PrivilegesRequired=lowest),
;   acceso en el menú Inicio del usuario y, si se marca la casilla, en el escritorio.
; - AppId FIJO: instalar una versión nueva encima sustituye a la anterior (misma carpeta, mismo desinstalador).
;   NO CAMBIARLO NUNCA: con otro AppId Windows lo vería como otro programa.
; - Si la app está abierta, el asistente la cierra (Restart Manager). Sin comprobar aún si Java lo recibe como su
;   cierre normal (windowClosing) o como un apagado sin él: en los dos casos el [InstallDelete] y el .cfg nuevo mandan.
; - El desinstalador NO borra los datos (%APPDATA%\aoe2radar) ni las recs (Documentos\aoe2radar\recs), y lo dice.
; - Idioma del asistente: el de Windows (español o inglés; cualquier otro, inglés).
; - Este archivo va en UTF-8 CON BOM: sin él, ISCC lo leería como ANSI y las tildes saldrían mal.
;
; Parámetros (/D en la línea de ISCC): AppVersion (obligatorio, version.app del pom), DistDir, OutputDir, Icono.

#ifndef AppVersion
  #error Falta /DAppVersion=X.Y: compila con el perfil instalador del pom
#endif
#ifndef DistDir
  #define DistDir "..\target\dist\aoe2radar"
#endif
#ifndef OutputDir
  #define OutputDir "..\target\instalador"
#endif
#ifndef Icono
  #define Icono "..\target\logo.ico"
#endif

#define AppNombre "aoe2radar"
#define AppExe "aoe2radar.exe"
#define AppUrl "https://github.com/jguardiola-dev/aoe2radar"

[Setup]
AppId={{71904EA2-8223-40DA-818A-8DC4B98ECEAF}
AppName={#AppNombre}
AppVersion={#AppVersion}
AppVerName={#AppNombre} {#AppVersion}
AppPublisher=12Tirador
AppPublisherURL={#AppUrl}
AppSupportURL={#AppUrl}/issues
AppUpdatesURL={#AppUrl}/releases
VersionInfoVersion={#AppVersion}
DefaultDirName={userpf}\{#AppNombre}
DisableDirPage=yes
DefaultGroupName={#AppNombre}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename={#AppNombre}-{#AppVersion}-setup
SetupIconFile={#Icono}
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppNombre}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; La app abierta tiene en uso el exe, las DLL del runtime y los jars de app\: el asistente la cierra antes de copiar.
CloseApplications=yes
CloseApplicationsFilter=*.exe,*.dll,*.jar
RestartApplications=no
ShowLanguageDialog=no
LanguageDetectionMethod=uilanguage

[Languages]
; El primero es el de reserva si el idioma de Windows no es ninguno de estos: inglés.
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[CustomMessages]
en.IconoEscritorio=Create a &desktop shortcut
es.IconoEscritorio=Crear un acceso directo en el &escritorio
en.DatosConservados=aoe2radar has been removed, but your data has not been deleted:%n%n%1%n%2 (or the recs folder you chose)%n%nDelete those folders by hand if you no longer want them.
es.DatosConservados=aoe2radar se ha desinstalado, pero tus datos no se han borrado:%n%n%1%n%2 (o la carpeta de recs que elegiste)%n%nBorra esas carpetas a mano si ya no las quieres.

[Tasks]
Name: "escritorio"; Description: "{cm:IconoEscritorio}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[InstallDelete]
; Los jars de la app que haya dejado una versión anterior o el actualizador propio (app\aoe2radar-X.jar): el .cfg
; nuevo solo nombra el suyo. Las dependencias (flatlaf…) y el runtime se sustituyen archivo a archivo.
Type: files; Name: "{app}\app\aoe2radar-*.jar"

[Files]
Source: "{#DistDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppNombre}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppNombre}"; Filename: "{app}\{#AppExe}"; Tasks: escritorio

[Registry]
; «Ejecutar al iniciar Windows» (Configuración de la app) escribe este valor; al desinstalar se quita para que
; Windows no intente abrir un exe que ya no existe. Al instalar no se crea nada.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "{#AppNombre}"; Flags: uninsdeletevalue dontcreatekey

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppNombre}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Lo que el actualizador propio puso en app\ después de instalar (el desinstalador solo conoce lo que copió él).
Type: files; Name: "{app}\app\aoe2radar-*.jar"
Type: files; Name: "{app}\app\*.parcial"
Type: files; Name: "{app}\app\*.tmp"
Type: dirifempty; Name: "{app}\app"
Type: dirifempty; Name: "{app}"

[Code]
// Ojo: en esta sección ninguna línea puede empezar por «[» (ISCC la tomaría por una sección nueva).
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Datos, Recs: String;
begin
  if CurUninstallStep = usPostUninstall then begin
    Datos := ExpandConstant('{userappdata}\{#AppNombre}');
    Recs := ExpandConstant('{userdocs}\{#AppNombre}\recs');
    SuppressibleMsgBox(FmtMessage(CustomMessage('DatosConservados'), [Datos, Recs]), mbInformation, MB_OK, IDOK);
  end;
end;
