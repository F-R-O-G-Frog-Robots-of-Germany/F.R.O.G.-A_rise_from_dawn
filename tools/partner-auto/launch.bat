@echo off
python "%~dp0import_partner_auto.py" --ui --detach
if errorlevel 1 pause
