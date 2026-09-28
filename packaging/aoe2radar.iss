; aoe2radar — instalador por usuario (Inno Setup 6). Lo compila el perfil «instalador» del pom
; (mvn -Pempaquetar,instalador -DskipTests verify) a partir del app-image de jpackage (target/dist/aoe2radar).
;
; - Por usuario, sin administrador: %LOCALAPPDATA%\Programs\aoe2radar ({userpf} con PrivilegesRequired=lowest),
;   acceso en el menú Inicio del usuario y, si se marca la casilla, en el escritorio.
; - AppId FIJO: instalar una versión nueva encima sustituye a la anterior (misma carpeta, mismo desinstalador).
;   NO CAMBIARLO NUNCA: con otro AppId Windows lo vería como otro programa.
; - Si la app está abierta, el asistente (y el desinstalador) la cierra como la × antes de tocar nada:
;   cerrar_aoe2radar.ps1. NO se usa el Restart Manager: el lanzador de jpackage son DOS procesos aoe2radar.exe
;   (lanzador + hijo con la JVM y la ventana), el Restart Manager no los cierra y un WM_CLOSE al lanzador lo rompe
;   («GetMessage() failed. System error 1400»). Ver README_TECNICO.
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
; La app abierta tiene en uso el exe, las DLL del runtime y los jars de app\: la cierra PrepareToInstall (abajo), no
; el Restart Manager.
CloseApplications=no
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
en.AbrirApp=Open aoe2radar
es.AbrirApp=Abrir aoe2radar
en.CierraLaApp=aoe2radar is still open. Close it and click Retry.
es.CierraLaApp=aoe2radar sigue abierta. Ciérrala y pulsa Reintentar.
en.SigueAbierta=aoe2radar is still open: close it and run the installer again.
es.SigueAbierta=aoe2radar sigue abierta: ciérrala y vuelve a ejecutar el instalador.
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
; El que cierra la app antes de instalar (se extrae a {tmp}) y de desinstalar (el que quedó en {app}).
Source: "cerrar_aoe2radar.ps1"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#AppNombre}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppNombre}"; Filename: "{app}\{#AppExe}"; Tasks: escritorio

[Registry]
; «Ejecutar al iniciar Windows» (Configuración de la app) escribe este valor; al desinstalar se quita para que
; Windows no intente abrir un exe que ya no existe. Al instalar no se crea nada.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "{#AppNombre}"; Flags: uninsdeletevalue dontcreatekey

[Run]
; Casilla marcada por defecto al final del asistente; en modo silencioso no se abre.
Filename: "{app}\{#AppExe}"; Description: "{cm:AbrirApp}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Lo que el actualizador propio puso en app\ después de instalar (el desinstalador solo conoce lo que copió él).
Type: files; Name: "{app}\app\aoe2radar-*.jar"
Type: files; Name: "{app}\app\*.parcial"
Type: files; Name: "{app}\app\*.tmp"
Type: dirifempty; Name: "{app}\app"
Type: dirifempty; Name: "{app}"

[Code]
// Ojo: en esta sección ninguna línea puede empezar por «[» (ISCC la tomaría por una sección nueva).

// Cierra la app (todas las copias que corren desde Dir) como la ×, con cerrar_aoe2radar.ps1: True si ya no queda
// ninguna. Lo que dice el script queda en el log de Inno (/LOG).
function CerrarApp(Script, Dir: String): Boolean;
var
  Salida, Params: String;
  Codigo, I: Integer;
  Lineas: TArrayOfString;
begin
  Result := True;
  if not FileExists(Script) then begin
    Log('cerrar_aoe2radar.ps1 no está: ' + Script);
    Exit;
  end;
  // GetTempDir y no {tmp}: vale igual en el instalador y en el desinstalador. Rutas entre comillas dobles (una ruta
  // de Windows no puede tenerlas, y {app} no acaba en barra).
  Salida := AddBackslash(GetTempDir) + 'aoe2radar_cerrar.txt';
  DeleteFile(Salida);
  Params := '-NoProfile -ExecutionPolicy Bypass -File "' + Script + '" -Dir "' + Dir + '" -Salida "' + Salida + '"';
  if not Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'), Params, '', SW_HIDE, ewWaitUntilTerminated, Codigo) then begin
    Log('no se pudo ejecutar PowerShell para cerrar la app (' + SysErrorMessage(Codigo) + ')');
    Codigo := 1;
  end;
  if LoadStringsFromFile(Salida, Lineas) then
    for I := 0 to GetArrayLength(Lineas) - 1 do Log('cerrar app: ' + Lineas[I]);
  DeleteFile(Salida);
  Result := Codigo = 0;
end;

// Instalar encima con la app abierta: se cierra antes de copiar. Interactivo: Reintentar/Cancelar mientras siga
// abierta. Silencioso: error en el log y el setup sale con código distinto de 0 (7: PrepareToInstall falló).
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Script: String;
begin
  Result := '';
  ExtractTemporaryFile('cerrar_aoe2radar.ps1');
  Script := ExpandConstant('{tmp}\cerrar_aoe2radar.ps1');
  while not CerrarApp(Script, ExpandConstant('{app}')) do begin
    if WizardSilent then begin
      Log('ERROR: aoe2radar sigue abierta; no se instala');
      Result := CustomMessage('SigueAbierta');
      Exit;
    end;
    if MsgBox(CustomMessage('CierraLaApp'), mbError, MB_RETRYCANCEL) <> IDRETRY then begin
      Result := CustomMessage('SigueAbierta');
      Exit;
    end;
  end;
end;

// Desinstalar con la app abierta: lo mismo, con el script que quedó instalado en {app}.
function InitializeUninstall(): Boolean;
var
  Script: String;
begin
  Result := True;
  Script := ExpandConstant('{app}\cerrar_aoe2radar.ps1');
  while not CerrarApp(Script, ExpandConstant('{app}')) do begin
    if UninstallSilent then begin
      Log('ERROR: aoe2radar sigue abierta; no se desinstala');
      Result := False;
      Exit;
    end;
    if MsgBox(CustomMessage('CierraLaApp'), mbError, MB_RETRYCANCEL) <> IDRETRY then begin
      Result := False;
      Exit;
    end;
  end;
end;
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
