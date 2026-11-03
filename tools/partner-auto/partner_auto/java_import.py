"""Static Java autonomous extraction. Imported source is parsed, never executed.

The JDK supplies syntax trees; a deliberately bounded Python interpreter evaluates
pure source expressions and recognizes common FTC path/action APIs. Unsupported
behavior is retained as an explicit unknown, not silently assigned zero time.
"""
from __future__ import annotations

import copy
from dataclasses import dataclass, field
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
from functools import lru_cache


ROOT = Path(__file__).resolve().parents[3]
BRIDGE = Path(__file__).resolve().parents[1] / "java" / "JavaAstDump.java"
MAX_STEPS = 100_000
MAX_LOOP = 1000
_BRIDGE_LOCK = threading.Lock()


@dataclass(frozen=True)
class Unknown:
    detail: str


@dataclass(frozen=True)
class Ref:
    name: str


@dataclass(frozen=True)
class Pose:
    x: float
    y: float
    heading: float = 0.0


@dataclass
class Instance:
    cls: str
    values: dict = field(default_factory=dict)


@dataclass
class Frame:
    cls: str
    receiver: Instance
    locals: dict = field(default_factory=dict)


@dataclass
class Builder:
    mode: str
    current: Pose | Unknown | None = None
    actions: list = field(default_factory=list)
    paths: list = field(default_factory=list)
    tangent: float = 0.0


class Returned(Exception):
    def __init__(self, value=None): self.value = value


class Broke(Exception): pass
class Continued(Exception): pass


def _java_tool(name: str) -> str:
    home = os.environ.get("JAVA_HOME")
    candidate = Path(home) / "bin" / (name + (".exe" if os.name == "nt" else "")) if home else None
    if candidate and candidate.is_file(): return str(candidate)
    found = shutil.which(name)
    if not found: raise ValueError("Java import requires JDK 17 or newer (java and javac on PATH).")
    return found


@lru_cache(maxsize=8)
def _parse_cached(items: tuple[tuple[str, str], ...]) -> dict:
    fingerprint = hashlib.sha256(BRIDGE.read_bytes()).hexdigest()[:20]
    classes = ROOT / "build" / "partner-auto" / "java-cache" / fingerprint
    classes.mkdir(parents=True, exist_ok=True)
    with _BRIDGE_LOCK:
        if not (classes / "JavaAstDump.class").is_file():
            process = subprocess.run([_java_tool("javac"), "-encoding", "UTF-8", "-proc:none", "-d", str(classes), str(BRIDGE)],
                                     capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
            if process.returncode: raise ValueError("Cannot compile the Java syntax reader: " + process.stderr[-3000:])
    with tempfile.TemporaryDirectory(prefix="partner-java-") as folder:
        paths, names = [], {}
        for index, (name, source) in enumerate(items):
            path = Path(folder) / f"Source{index}.java"
            path.write_text(source, encoding="utf-8")
            paths.append(str(path)); names[str(path)] = name
        # Only this bundled parser is compiled/executed. Supplied source goes to parse(),
        # with annotation processing disabled and no analyze()/generate()/class loading.
        manifest = Path(folder) / "sources.txt"
        manifest.write_text("\n".join(paths) + "\n", encoding="utf-8")
        process = subprocess.run([_java_tool("java"), "-Xmx512m", "-cp", str(classes), "JavaAstDump", "--source-list", str(manifest)],
                                 capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=45)
        if process.returncode: raise ValueError("Java syntax reader failed: " + process.stderr[-3000:])
        result = json.loads(process.stdout)
        for unit in result["units"]: unit["file"] = names.get(unit["file"], unit["file"])
        for problem in result["diagnostics"]:
            problem["source"] = {"file": names.get(problem.pop("file", ""), ""), "line": problem.pop("line", 1)}
        return result


def _duration(lo, hi=None, basis="source"):
    return {"min_s": lo, "max_s": lo if hi is None else hi, "basis": basis}


def _unknown_action(detail, source=None):
    return {"kind": "unknown", "label": detail, "duration": {"min_s": 0, "max_s": None, "basis": "unknown"},
            **({"source": source} if source else {})}


def _is_number(value): return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)
def _wrap(value): return (value + math.pi) % (2 * math.pi) - math.pi


class Interpreter:
    def __init__(self, parsed, options):
        self.classes, self.units = {}, {}
        self.diagnostics = copy.deepcopy(parsed["diagnostics"])
        self.options = options
        self.actions = []
        self.statics = {}
        self.evaluating = set()
        self.initialized_statics = set()
        self.steps = 0
        self.depth = 0
        self.position = None
        self.pending_async = None
        for unit in parsed["units"]:
            for tree in unit["types"]:
                if tree and tree["kind"] in ("CLASS", "ENUM", "INTERFACE", "RECORD"):
                    self.add_class(tree, unit, unit["package"])

    def add_class(self, tree, unit, prefix):
        name = (prefix + "." if prefix else "") + tree["name"]
        tree = dict(tree, qualified_name=name, unit=unit)
        if name in self.classes:
            self.problem("error", "DUPLICATE_CLASS", "Duplicate Java class: " + name, tree)
        self.classes[name] = tree
        for member in tree["members"]:
            if member and member["kind"] in ("CLASS", "ENUM", "INTERFACE", "RECORD"):
                self.add_class(member, unit, name)

    def source(self, node, cls=None):
        owner = self.classes.get(cls or getattr(self, "entry", ""))
        return {"file": owner["unit"]["file"] if owner else "", "line": node.get("line", 1) if node else 1}

    def problem(self, severity, code, message, node=None, cls=None):
        item = {"severity": severity, "code": code, "message": message}
        if node: item["source"] = self.source(node, cls)
        for existing in self.diagnostics:
            if existing == item: return existing
        self.diagnostics.append(item)
        return item

    def resolve(self, name, cls):
        name = name.split("<", 1)[0].replace("[]", "").strip()
        if name in self.classes: return name
        owner = self.classes.get(cls)
        if not owner: return None
        unit = owner["unit"]
        head, sep, tail = name.partition(".")
        for imp in unit["imports"]:
            target = imp["name"]
            if not target.endswith(".*") and target.rsplit(".", 1)[-1] == head:
                candidate = target + ("." + tail if sep else "")
                if candidate in self.classes: return candidate
        parent = cls
        while parent:
            candidate = parent + "." + name
            if candidate in self.classes: return candidate
            parent = parent.rsplit(".", 1)[0] if "." in parent else ""
        candidate = (unit["package"] + "." if unit["package"] else "") + name
        if candidate in self.classes: return candidate
        for imp in unit["imports"]:
            if imp["name"].endswith(".*"):
                candidate = imp["name"][:-1] + name
                if candidate in self.classes: return candidate
        matches = [key for key in self.classes if key == name or key.endswith("." + name)]
        return matches[0] if len(matches) == 1 else None

    def hierarchy(self, cls):
        seen, pending = set(), [cls]
        while pending:
            cls = pending.pop(0)
            if not cls or cls not in self.classes or cls in seen: continue
            yield cls; seen.add(cls)
            tree = self.classes[cls]
            parents = ([tree["extends"]] if tree.get("extends") else []) + tree.get("implements", [])
            pending[0:0] = [self.resolve(parent, cls) for parent in parents]

    def method(self, cls, name, count=None):
        for owner in self.hierarchy(cls):
            candidates = [x for x in self.classes[owner]["members"] if x and x["kind"] == "METHOD" and x["name"] == name]
            for item in candidates:
                n = len(item["parameters"])
                if count is None or count == n or item.get("varargs") and count >= n - 1:
                    return owner, item
        return None

    def field(self, cls, name, receiver):
        for owner in self.hierarchy(cls):
            self.ensure_static(owner)
            for item in self.classes[owner]["members"]:
                if item and item["kind"] == "VARIABLE" and item["name"] == name:
                    key = (owner, name)
                    cache = self.statics if "static" in item.get("modifiers", "") else receiver.values
                    if key in cache: return cache[key]
                    if key in self.evaluating: return Unknown("Cyclic field " + name)
                    self.evaluating.add(key)
                    try:
                        value = self.coerce(self.expr(item.get("value"), Frame(owner, receiver)), item.get("type", ""))
                        cache[key] = value
                        return value
                    finally: self.evaluating.discard(key)
        return Unknown("Unresolved field " + name)

    def ensure_static(self, cls):
        if cls in self.initialized_statics: return
        self.initialized_statics.add(cls)
        for item in self.classes[cls]["members"]:
            if item and item["kind"] == "BLOCK" and item.get("static"):
                self.statement(item, Frame(cls, Instance(cls)))

    def identifier(self, name, frame):
        if name in frame.locals: return frame.locals[name]
        if name == "this": return frame.receiver
        if name == "super":
            parent = self.classes[frame.cls].get("extends")
            return Ref("super:" + (self.resolve(parent, frame.cls) or parent))
        value = self.field(frame.cls, name, frame.receiver)
        if not isinstance(value, Unknown): return value
        outer = frame.cls.rsplit(".", 1)[0] if "." in frame.cls else ""
        while outer in self.classes:
            value = self.field(outer, name, Instance(outer))
            if not isinstance(value, Unknown): return value
            outer = outer.rsplit(".", 1)[0] if "." in outer else ""
        resolved = self.resolve(name, frame.cls)
        if resolved: return Ref(resolved)
        for imp in self.classes[frame.cls]["unit"]["imports"]:
            if imp["static"] and (imp["name"].endswith("." + name) or imp["name"].endswith(".*")):
                owner = imp["name"].rsplit(".", 1)[0]
                if owner in self.classes:
                    value = self.field(owner, name, Instance(owner))
                    if not isinstance(value, Unknown): return value
        # An external type/object is a symbolic reference, never a Python object or executable.
        return Ref(name)

    def tick(self):
        self.steps += 1
        if self.steps > MAX_STEPS: raise ValueError("Java extraction exceeded its bounded interpretation limit")

    def expr(self, node, frame):
        self.tick()
        if node is None: return None
        k = node["kind"]
        if k.endswith("_LITERAL"): return node.get("value")
        if k == "IDENTIFIER": return self.identifier(node["name"], frame)
        if k == "PARENTHESIZED": return self.expr(node["expression"], frame)
        if k == "TYPE_CAST":
            value = self.expr(node["expression"], frame)
            return self.coerce(value, node.get("type", ""))
        if k == "MEMBER_SELECT":
            obj = self.expr(node["object"], frame); name = node["name"]
            if isinstance(obj, Ref):
                if obj.name == "Math" and name in ("PI", "E"): return math.pi if name == "PI" else math.e
                if name in ("RED", "BLUE") and obj.name.rsplit(".", 1)[-1] == "Alliance": return name
                candidate = obj.name + "." + name
                resolved = self.resolve(candidate, frame.cls)
                if resolved: return Ref(resolved)
                if obj.name in self.classes:
                    value = self.field(obj.name, name, Instance(obj.name))
                    if not isinstance(value, Unknown): return value
                return Ref(candidate)
            if isinstance(obj, Instance): return self.field(obj.cls, name, obj)
            if isinstance(obj, Pose) and name in ("x", "y", "heading"): return getattr(obj, name)
            if isinstance(obj, list) and name == "length": return len(obj)
            return Unknown("Unresolved member " + name)
        if k == "METHOD_INVOCATION":
            select = node["select"]
            if select["kind"] == "MEMBER_SELECT":
                receiver = self.expr(select["object"], frame); name = select["name"]
            else: receiver = None; name = select.get("name", "?")
            args = [self.expr(arg, frame) for arg in node["args"]]
            return self.call(receiver, name, args, frame, node)
        if k == "NEW_CLASS":
            args = [self.expr(arg, frame) for arg in node["args"]]
            short = node["type"].split("<", 1)[0].rsplit(".", 1)[-1]
            if short in ("Pose", "Pose2d", "Point", "Vector2d"):
                if len(args) == 1 and isinstance(args[0], Pose): return args[0]
                if short == "Pose2d" and len(args) == 2 and isinstance(args[0], Pose) and _is_number(args[1]):
                    return Pose(args[0].x, args[0].y, args[1])
                if short == "Point" and len(args) >= 2 and all(_is_number(x) for x in args[:2]):
                    if len(args) == 2 or isinstance(args[2], Ref) and args[2].name.endswith(".CARTESIAN"):
                        return Pose(float(args[0]), float(args[1]))
                    if isinstance(args[2], Ref) and args[2].name.endswith(".POLAR"):
                        return Pose(args[0]*math.cos(args[1]), args[0]*math.sin(args[1]))
                    return Unknown("Unknown Point coordinate-system flag")
                if len(args) >= 2 and all(_is_number(x) for x in args[:min(3, len(args))]):
                    return Pose(float(args[0]), float(args[1]), float(args[2]) if len(args) >= 3 else 0)
                return Unknown("Non-numeric pose constructor")
            if short in ("BezierCurve", "BezierLine"):
                return self.path(args, "line" if short == "BezierLine" else "bezier", {"type": "tangent"}, node, frame)
            if short == "Path" and len(args) == 1 and isinstance(args[0],dict) and args[0].get("_path"):
                return args[0]
            if short in ("SleepAction", "WaitCommand"):
                seconds = args[0] if args else Unknown("Missing wait duration")
                if short == "WaitCommand" and _is_number(seconds): seconds /= 1000
                return self.timed("wait", seconds, node, frame)
            if short in ("SequentialAction", "SequentialCommandGroup", "ParallelAction", "ParallelCommandGroup"):
                return {"kind": "parallel" if "Parallel" in short else "sequence", "children": self.action_values(args), "source": self.source(node, frame.cls)}
            if short in ("ArrayList", "LinkedList"): return list(args[0]) if args and isinstance(args[0], list) else []
            if short in ("PathBuilder", "TrajectorySequenceBuilder"): return Builder("pedro" if short == "PathBuilder" else "rr", args[0] if args else self.position)
            resolved = self.resolve(node["type"], frame.cls)
            if resolved:
                instance = Instance(resolved)
                constructor = self.method(resolved, "<init>", len(args))
                if constructor: self.invoke(instance, constructor, args, node)
                return instance
            return Ref("new:" + short)
        if k == "NEW_ARRAY":
            if node.get("values") is not None: return [self.expr(x, frame) for x in node["values"]]
            sizes = [self.expr(x, frame) for x in node.get("dimensions", [])]
            return [None] * int(sizes[0]) if len(sizes) == 1 and _is_number(sizes[0]) and 0 <= sizes[0] <= MAX_LOOP else Unknown("Unknown array size")
        if k == "ARRAY_ACCESS":
            arr, index = self.expr(node["object"], frame), self.expr(node["index"], frame)
            if isinstance(arr, list) and _is_number(index) and 0 <= int(index) < len(arr): return arr[int(index)]
            return Unknown("Unknown/out-of-range array index")
        if k == "CONDITIONAL_EXPRESSION":
            condition = self.expr(node["condition"], frame)
            if isinstance(condition, bool): return self.expr(node["yes"] if condition else node["no"], frame)
            return Unknown("Conditional expression depends on runtime input")
        if k == "ASSIGNMENT" or k.endswith("_ASSIGNMENT"):
            value = self.expr(node["value"], frame)
            if k != "ASSIGNMENT":
                old = self.expr(node["target"], frame)
                value = self.binary(k[:-11], old, value)
            self.assign(node["target"], value, frame); return value
        if k in ("POSTFIX_INCREMENT", "PREFIX_INCREMENT", "POSTFIX_DECREMENT", "PREFIX_DECREMENT"):
            old = self.expr(node["expression"], frame)
            new = old + (1 if "INCREMENT" in k else -1) if _is_number(old) else Unknown("Unknown increment")
            self.assign(node["expression"], new, frame)
            return old if k.startswith("POSTFIX") else new
        if k in ("UNARY_MINUS", "UNARY_PLUS", "LOGICAL_COMPLEMENT", "BITWISE_COMPLEMENT"):
            value = self.expr(node["expression"], frame)
            if k == "LOGICAL_COMPLEMENT": return not value if isinstance(value, bool) else Unknown("Unknown boolean")
            if not _is_number(value): return Unknown("Unknown unary operand")
            return -value if k == "UNARY_MINUS" else value if k == "UNARY_PLUS" else ~int(value)
        if "left" in node:
            left = self.expr(node["left"], frame)
            if k == "CONDITIONAL_AND" and left is False: return False
            if k == "CONDITIONAL_OR" and left is True: return True
            return self.binary(k, left, self.expr(node["right"], frame))
        if k == "LAMBDA_EXPRESSION" or k == "MEMBER_REFERENCE": return ("lambda", node, frame)
        return Unknown("Unsupported expression " + k)

    def assign(self, target, value, frame):
        if target["kind"] == "IDENTIFIER":
            name = target["name"]
            if name in frame.locals: frame.locals[name] = value; return
            for owner in self.hierarchy(frame.cls):
                declared = next((x for x in self.classes[owner]["members"] if x and x["kind"] == "VARIABLE" and x["name"] == name), None)
                if declared:
                    cache = self.statics if "static" in declared.get("modifiers", "") else frame.receiver.values
                    cache[(owner, name)] = value; return
            frame.locals[name] = value
        elif target["kind"] == "MEMBER_SELECT":
            receiver = self.expr(target["object"], frame)
            if isinstance(receiver, Instance):
                owner = next((owner for owner in self.hierarchy(frame.cls)
                              if any(x and x["kind"] == "VARIABLE" and x["name"] == target["name"]
                                     for x in self.classes[owner]["members"])), receiver.cls)
                receiver.values[(owner, target["name"])] = value
            elif isinstance(receiver, Ref) and receiver.name in self.classes:
                self.statics[(receiver.name,target["name"])] = value
        elif target["kind"] == "ARRAY_ACCESS":
            arr, index = self.expr(target["object"], frame), self.expr(target["index"], frame)
            if isinstance(arr, list) and _is_number(index) and 0 <= int(index) < len(arr): arr[int(index)] = value

    def binary(self, kind, left, right):
        if isinstance(left, (Unknown, Ref, Instance)) or isinstance(right, (Unknown, Ref, Instance)):
            if isinstance(left, Ref) and isinstance(right, Ref) and kind in ("EQUAL_TO", "NOT_EQUAL_TO"):
                return left.name == right.name if kind == "EQUAL_TO" else left.name != right.name
            return Unknown("Runtime operand in " + kind)
        try:
            if kind == "PLUS": return left + right
            if kind == "MINUS": return left - right
            if kind == "MULTIPLY": return left * right
            if kind == "DIVIDE":
                if isinstance(left,int) and isinstance(right,int): return math.trunc(left/right)
                return left / right
            if kind == "REMAINDER": return left % right
            if kind == "LESS_THAN": return left < right
            if kind == "LESS_THAN_EQUAL": return left <= right
            if kind == "GREATER_THAN": return left > right
            if kind == "GREATER_THAN_EQUAL": return left >= right
            if kind == "EQUAL_TO": return left == right
            if kind == "NOT_EQUAL_TO": return left != right
            if kind == "CONDITIONAL_AND": return bool(left and right)
            if kind == "CONDITIONAL_OR": return bool(left or right)
            if kind == "AND": return left & right
            if kind == "OR": return left | right
        except (TypeError, ValueError, ZeroDivisionError, OverflowError): pass
        return Unknown("Unsupported or invalid operator " + kind)

    @staticmethod
    def coerce(value, java_type):
        if _is_number(value):
            if java_type in ("double", "float", "Double", "Float"): return float(value)
            if java_type in ("int", "long", "Integer", "Long"): return int(value)
            if java_type in ("byte", "short"):
                bits = 8 if java_type == "byte" else 16
                return (int(value) + 2**(bits-1)) % 2**bits - 2**(bits-1)
        return value

    def path(self, values, kind, heading, node, frame):
        if len(values) == 1 and isinstance(values[0], list): values = values[0]
        if len(values) < 2 or not all(isinstance(p, Pose) for p in values):
            unresolved = [p.name if isinstance(p, Ref) else p.detail if isinstance(p, Unknown) else repr(p)
                          for p in values if not isinstance(p, Pose)]
            return Unknown("Path contains unresolved poses" + (": " + ", ".join(unresolved[:5]) if unresolved else ""))
        return {"_path": True, "path": {"points": [[p.x, p.y] for p in values], "kind": kind, "heading": heading},
                "_poses": values, "source": self.source(node, frame.cls)}

    def timed(self, kind, seconds, node, frame):
        if not _is_number(seconds) or seconds < 0:
            self.problem("error", "UNKNOWN_DURATION", "Cannot determine a finite nonnegative " + kind + " duration", node, frame.cls)
            return _unknown_action("Unknown " + kind + " duration", self.source(node, frame.cls))
        return {"kind": kind, "duration": _duration(float(seconds)), "source": self.source(node, frame.cls)}

    def action_values(self, values):
        result = []
        for value in values:
            if isinstance(value, list): result.extend(self.action_values(value))
            elif isinstance(value, dict) and "kind" in value: result.append(value)
            elif isinstance(value, dict) and value.get("_path"):
                result.append({"kind": "move", "path": value["path"], "source": value["source"]})
            elif isinstance(value, Unknown): result.append(_unknown_action(value.detail))
            elif isinstance(value, (Ref, Instance)): result.append(_unknown_action("Unresolved action " + (value.name if isinstance(value, Ref) else value.cls)))
        return result

    def append_action(self, value, node, frame):
        values = self.action_values([value])
        if not values:
            values = [_unknown_action("Unresolved autonomous action", self.source(node, frame.cls))]
        for action in values:
            if action["kind"] == "unknown":
                self.problem("error", "UNRESOLVED_ACTION", action.get("label", "Unknown autonomous action"), node, frame.cls)
            self.actions.append(action)
            if action["kind"] == "move" and action.get("path", {}).get("points"):
                x, y = action["path"]["points"][-1]
                heading = action["path"].get("heading", {})
                self.position = Pose(x, y, heading.get("end_rad", heading.get("start_rad", 0)))

    def endpoint(self, actions, position):
        for action in actions:
            if action["kind"] == "move" and action.get("path", {}).get("points"):
                x,y = action["path"]["points"][-1]
                h = action["path"].get("heading", {})
                position = Pose(x,y,h.get("end_rad",h.get("start_rad",0)))
            elif action["kind"] == "sequence": position = self.endpoint(action.get("children", []),position)
            elif action["kind"] in ("parallel", "branch", "unknown"): position = Unknown("Concurrent/runtime-dependent endpoint")
        return position

    def dispatch_async(self, value, name, node, frame):
        first = len(self.actions)
        origin = self.position
        self.append_action(value,node,frame)
        end = self.endpoint(self.actions[first:],origin)
        message = name + " dispatches asynchronously; completion/order requires a recognized wait"
        problem = self.problem("error", "ASYNC_COMPLETION", message,node,frame.cls)
        barrier = _unknown_action(message,self.source(node,frame.cls))
        self.actions.append(barrier)
        self.pending_async = {"barrier":barrier,"problem":problem,"end":end}
        self.position = Unknown("Asynchronous movement has not completed")

    def finish_async_wait(self):
        pending = self.pending_async
        if pending and self.actions and self.actions[-1] is pending["barrier"]:
            self.actions.pop()
            if pending["problem"] in self.diagnostics: self.diagnostics.remove(pending["problem"])
            self.position = pending["end"]
            self.pending_async = None
            return True
        return False

    def recognized_busy_wait(self,node):
        condition = node.get("condition")
        while condition and condition["kind"] == "PARENTHESIZED": condition = condition["expression"]
        if not condition or condition["kind"] != "METHOD_INVOCATION": return False
        select = condition["select"]
        if select["kind"] != "MEMBER_SELECT" or select["name"] not in ("isBusy","is_busy"): return False
        receiver = select["object"]
        if receiver["kind"] != "IDENTIFIER" or receiver["name"] not in ("drive","follower"): return False
        statements = node["body"].get("statements",[]) if node["body"]["kind"] == "BLOCK" else [node["body"]]
        for statement in statements:
            if statement["kind"] == "EMPTY_STATEMENT": continue
            if statement["kind"] != "EXPRESSION_STATEMENT": return False
            expression = statement["expression"]
            if expression["kind"] != "METHOD_INVOCATION": return False
            call = expression["select"]
            if call["kind"] == "IDENTIFIER" and call["name"] == "idle": continue
            if call["kind"] != "MEMBER_SELECT" or call["name"] not in ("update","updatePoseEstimate"): return False
            obj = call["object"]
            if obj["kind"] != "IDENTIFIER" or obj["name"] not in ("drive","follower"): return False
        return True

    def call(self, receiver, name, args, frame, node):
        self.tick()
        ref = receiver.name if isinstance(receiver, Ref) else ""
        short = ref.rsplit(".", 1)[-1]
        if receiver is None and name in ("this", "super"):
            cls = frame.cls if name == "this" else self.resolve(self.classes[frame.cls].get("extends", ""), frame.cls)
            # Zero-argument base construction was already interpreted base-to-leaf.
            if name == "super" and not args: return None
            found = self.method(cls, "<init>", len(args)) if cls else None
            return self.invoke(frame.receiver, found, args, node) if found else Unknown("Unresolved constructor delegation")
        target = frame.receiver if receiver is None else receiver
        declared = self.method(target.cls, name, len(args)) if isinstance(target, Instance) else None
        if declared and name not in ("queue_path", "queue_intake_path", "queue_shoot_path", "queue_shot"):
            return self.invoke(target, declared, args, node)
        # Library intrinsics contain no imported executable code.
        if short == "Math":
            operations = {"toRadians": math.radians, "toDegrees": math.degrees, "sin": math.sin, "cos": math.cos,
                          "atan2": math.atan2, "hypot": math.hypot, "sqrt": math.sqrt, "abs": abs,
                          "min": min, "max": max, "pow": pow, "round": round}
            if name in operations and all(_is_number(a) for a in args):
                try: return operations[name](*args)
                except (ValueError, OverflowError): return Unknown("Invalid Math." + name)
        if short in ("Double", "Float") and name == "isFinite": return len(args) == 1 and _is_number(args[0])
        if short in ("List", "Arrays") and name in ("of", "asList"): return args
        if short == "Collections" and name == "singletonList": return args
        if short == "SensorReadings" and name in ("are_valid", "is_valid"): return all(_is_number(a) for a in args)
        if isinstance(receiver, Pose):
            if name in ("x", "y", "heading", "getX", "getY", "getHeading"):
                return getattr(receiver, {"getX": "x", "getY": "y", "getHeading": "heading"}.get(name, name))
            if name in ("plus", "minus") and len(args) == 1 and isinstance(args[0], Pose):
                sign = 1 if name == "plus" else -1; p = args[0]
                return Pose(receiver.x + sign*p.x, receiver.y + sign*p.y, receiver.heading + sign*p.heading)
            if name in ("withHeading", "with_heading") and args and _is_number(args[0]): return Pose(receiver.x, receiver.y, args[0])
        if _is_number(receiver) and name == "toDouble" and not args: return receiver
        if short == "PoseFactory" and name in ("degrees", "radians"): return {"_factory": name, "mirror": None}
        if isinstance(receiver, dict) and "_factory" in receiver:
            if name == "mirrorAroundPoint" and len(args) >= 2:
                return dict(receiver, mirror=args[:2])
            if name == "of" and len(args) >= 2 and all(_is_number(a) for a in args[:3]):
                h = float(args[2]) if len(args) >= 3 else 0
                if receiver["_factory"] == "degrees": h = math.radians(h)
                x, y = float(args[0]), float(args[1])
                if receiver["mirror"]:
                    x, y = 2*receiver["mirror"][0]-x, 2*receiver["mirror"][1]-y; h = _wrap(h + math.pi)
                return Pose(x, y, h)
        if short == "Paths" and name in ("line", "curve"):
            return self.path(args, "line" if name == "line" else "bezier", {"type": "tangent"}, node, frame)
        if isinstance(receiver, dict) and receiver.get("_path"):
            if name in ("tangent", "constant", "linear", "setConstantHeadingInterpolation", "setLinearHeadingInterpolation", "setTangentHeadingInterpolation"):
                mode = "tangent" if "Tangent" in name or name == "tangent" else "linear" if "Linear" in name or name == "linear" else "constant"
                h = lambda a: a.heading if isinstance(a, Pose) else a
                heading = {"type": mode}
                if args and _is_number(h(args[0])):
                    heading["start_rad"] = h(args[0]); heading["end_rad"] = h(args[-1]) if mode == "linear" else h(args[0])
                elif mode != "tangent": return Unknown("Unknown path heading")
                result = copy.deepcopy(receiver); result["path"]["heading"] = heading; return result
            if name in ("with", "build", "reversed", "setReversed"): return receiver
        if isinstance(receiver, list):
            if name in ("add", "addAll"):
                receiver.extend(args[0] if name == "addAll" and args and isinstance(args[0], list) else args); return True
            if name == "get" and args and _is_number(args[0]) and 0 <= args[0] < len(receiver): return receiver[int(args[0])]
            if name in ("size", "length"): return len(receiver)
            if name == "clear": receiver.clear(); return None
        if name in ("pathBuilder", "trajectorySequenceBuilder", "trajectoryBuilder", "actionBuilder"):
            mode = "pedro" if name == "pathBuilder" else "rr"
            return Builder(mode, args[0] if args and isinstance(args[0], Pose) else self.position)
        if isinstance(receiver, Builder): return self.builder_call(receiver, name, args, frame, node)
        if name in ("followPath", "follow", "followPathAsync", "followTrajectoryAsync", "followTrajectorySequenceAsync", "schedule"):
            if args:
                self.dispatch_async(args[0],name,node,frame); return None
        if name in ("followTrajectory", "followTrajectorySequence", "runBlocking"):
            if args:
                self.append_action(args[0],node,frame); return None
        if name in ("waitSeconds", "sleep"):
            seconds = args[0] if args else Unknown("Missing wait")
            if name == "sleep" and _is_number(seconds): seconds /= 1000
            self.append_action(self.timed("wait", seconds, node, frame), node, frame); return None
        if name == "setStartingPose" or name == "setPoseEstimate" or name == "setPose":
            if args and isinstance(args[0], Pose):
                if not self.actions: self.position = args[0]
                else:
                    message = "Pose reset after autonomous actions requires coordinate-frame review"
                    self.problem("error","POSE_RESET_DURING_AUTO",message,node,frame.cls)
                    self.actions.append(_unknown_action(message,self.source(node,frame.cls)))
                    self.position = Unknown(message)
            return None
        if name in ("waitForStart", "idle", "waitForIdle"):
            if name == "waitForIdle":
                if not self.finish_async_wait():
                    self.problem("error","UNRESOLVED_COMPLETION_WAIT","Completion wait cannot resolve intervening asynchronous actions",node,frame.cls)
                    self.actions.append(_unknown_action("Completion wait requires review",self.source(node,frame.cls)))
                self.problem("warning", "FOLLOWER_WAIT", "Follower completion is represented by movement timing estimates, not an exact source duration", node, frame.cls)
            return None
        if name in ("opModeInInit", "isStopRequested"): return False
        if name in ("opModeIsActive", "isStarted"): return Unknown("Runtime OpMode state")
        # FROG queue adapter: inspect inherited source to ensure this API is present.
        method = declared
        if method and name in ("queue_path", "queue_intake_path", "queue_shoot_path", "queue_shot"):
            owner = self.classes[method[0]]
            if owner["name"] == "General" and self.method(method[0], "execute_shot"):
                if name == "queue_shot":
                    action = self.timed("shoot", args[0] if args else None, node, frame)
                    if action["kind"] == "shoot":
                        timeout = self.field(method[0], "SHOOTER_WAIT_TIMEOUT_SECONDS", target)
                        if not _is_number(timeout): timeout = 3.0
                        action["duration"]["max_s"] += timeout
                        action["readiness_timeout_s"] = timeout
                        action["on_readiness_timeout"] = "abort"
                        self.problem("warning", "SHOT_READINESS_RANGE", "Shot occupancy includes up to " + str(timeout) + " s of readiness waiting; timeout aborts the auto", node, frame.cls)
                    self.append_action(action, node, frame)
                else:
                    first_action = len(self.actions)
                    self.append_action(args[0] if args else Unknown("Missing queued path"), node, frame)
                    timeout = self.field(method[0], "PATH_TIMEOUT_SECONDS", target)
                    for action in self.actions[first_action:]:
                        action["intake"] = name == "queue_intake_path"
                        if _is_number(timeout):
                            action["timeout_s"] = timeout
                            action["on_timeout"] = "abort"
                    if name == "queue_shoot_path": self.call(None, "queue_shot", args[1:], frame, node)
                return None
        if method: return self.invoke(target, method, args, node)
        if ref.startswith("super:"):
            method = self.method(ref[6:], name, len(args))
            if method: return self.invoke(frame.receiver, method, args, node)
        if ref in self.classes:
            method = self.method(ref, name, len(args))
            if method: return self.invoke(Instance(ref), method, args, node)
        if receiver is None:
            for imp in self.classes[frame.cls]["unit"]["imports"]:
                if imp["static"] and (imp["name"].endswith("."+name) or imp["name"].endswith(".*")):
                    cls = imp["name"].rsplit(".", 1)[0]
                    method = self.method(cls, name, len(args)) if cls in self.classes else None
                    if method: return self.invoke(Instance(cls), method, args, node)
        # Small known telemetry/mechanism operations are not claimed to alter motion.
        if short in ("telemetry", "RobotLog") and name in ("addData", "addLine", "update", "dd", "ww"): return None
        return Unknown("Unsupported call " + (ref + "." if ref else "") + name)

    def builder_call(self, builder, name, args, frame, node):
        if name == "build":
            if builder.mode == "pedro": return {"kind": "sequence", "children": self.action_values(builder.paths), "source": self.source(node, frame.cls)}
            return {"kind": "sequence", "children": builder.actions, "source": self.source(node, frame.cls)}
        if builder.mode == "pedro":
            if name == "addPath":
                path = args[0] if len(args) == 1 else self.path(args, "bezier", {"type": "tangent"}, node, frame)
                builder.paths.append(path); return builder
            if name in ("setConstantHeadingInterpolation", "setLinearHeadingInterpolation", "setTangentHeadingInterpolation") and builder.paths:
                builder.paths[-1] = self.call(builder.paths[-1], name, args, frame, node); return builder
            if name in ("setPathEndTimeoutConstraint", "setPathEndTValueConstraint", "setZeroPowerAccelerationMultiplier", "setReversed"):
                self.problem("warning", "MOTION_CONSTRAINT", "Imported " + name + "; movement timing still requires a motion model", node, frame.cls); return builder
            return Unknown("Unsupported Pedro builder operation " + name)
        if name in ("setTangent", "setReversed", "setVelConstraint", "setAccelConstraint", "resetVelConstraint", "resetAccelConstraint"):
            if name == "setTangent" and args and _is_number(args[0]): builder.tangent = args[0]
            return builder
        if name in ("waitSeconds",):
            builder.actions.append(self.timed("wait", args[0] if args else None, node, frame)); return builder
        if name in ("stopAndAdd", "afterTime", "afterDisp", "addTemporalMarker", "UNSTABLE_addTemporalMarkerOffset"):
            if name == "stopAndAdd" and args: builder.actions.extend(self.action_values([args[0]]))
            else:
                builder.actions.append(_unknown_action("Concurrent action marker " + name + " requires review", self.source(node, frame.cls)))
                self.problem("error", "ACTION_MARKER", "Concurrent marker timing is unresolved: " + name, node, frame.cls)
            return builder
        if name in ("turn", "turnTo"):
            if not args or not _is_number(args[0]) or not isinstance(builder.current, Pose):
                builder.actions.append(_unknown_action("Unknown turn", self.source(node, frame.cls)))
            else:
                angle = args[0] if name == "turn" else _wrap(args[0] - builder.current.heading)
                builder.actions.append({"kind": "turn", "angle_rad": angle, "source": self.source(node, frame.cls)})
                builder.current = Pose(builder.current.x, builder.current.y, builder.current.heading + angle)
            return builder
        if name.startswith(("strafeTo", "lineTo", "splineTo")) or name in ("forward", "back", "strafeLeft", "strafeRight", "lineToX", "lineToY"):
            start = builder.current
            target = args[0] if args else None
            if name in ("forward", "back", "strafeLeft", "strafeRight") and isinstance(start, Pose) and _is_number(target):
                a = start.heading + (math.pi / 2 if name in ("strafeLeft", "strafeRight") else 0)
                distance = target * (-1 if name in ("back", "strafeRight") else 1)
                target = Pose(start.x + math.cos(a)*distance, start.y + math.sin(a)*distance, start.heading)
            if name in ("lineToX", "lineToY") and isinstance(start, Pose) and _is_number(target):
                target = Pose(target if name == "lineToX" else start.x, target if name == "lineToY" else start.y, start.heading)
            if not isinstance(start, Pose) or not isinstance(target, Pose):
                builder.actions.append(_unknown_action("Unresolved Road Runner path " + name, self.source(node, frame.cls))); builder.current = Unknown("Unknown path endpoint"); return builder
            h = target.heading if "Heading" in name and "Constant" not in name else args[1] if name == "strafeToLinearHeading" and len(args) > 1 else start.heading
            if not _is_number(h): h = start.heading
            heading = {"type": "linear" if h != start.heading else "constant", "start_rad": start.heading, "end_rad": h}
            if name.startswith("splineTo"):
                # Road Runner's Hermite spline is not a Bezier with just endpoints.
                # Keep endpoints for review while explicitly blocking exact corridor claims.
                builder.actions.append(_unknown_action("Road Runner spline geometry needs its framework trajectory adapter", self.source(node, frame.cls)))
                self.problem("error", "RR_SPLINE_GEOMETRY", "Spline endpoints alone do not establish its swept path: " + name, node, frame.cls)
            else:
                builder.actions.append({"kind": "move", "path": {"kind": "line", "points": [[start.x,start.y],[target.x,target.y]], "heading": heading}, "source": self.source(node, frame.cls)})
            builder.current = Pose(target.x, target.y, h)
            return builder
        return Unknown("Unsupported Road Runner builder operation " + name)

    def invoke(self, receiver, found, args, node):
        owner, method = found
        if self.depth >= 40:
            self.problem("error", "RECURSION_LIMIT", "Method recursion limit reached", node, owner)
            return Unknown("Method recursion limit reached")
        values = {}
        params = method["parameters"]
        for index, param in enumerate(params):
            if method.get("varargs") and index == len(params)-1:
                values[param["name"]] = args[index] if len(args) == index+1 and isinstance(args[index], list) else args[index:]
            else: values[param["name"]] = self.coerce(args[index], param.get("type", "")) if index < len(args) else Unknown("Missing argument " + param["name"])
        self.depth += 1
        try:
            self.statement(method.get("body"), Frame(owner, receiver, values))
        except Returned as result: return result.value
        finally: self.depth -= 1
        return None

    def capture(self, node, frame):
        prior = self.actions; self.actions = []
        statics, position, pending = self.statics, self.position, self.pending_async
        self.statics = copy.deepcopy(statics)
        self.pending_async = None
        try:
            try: self.statement(node, copy.deepcopy(frame))
            except (Returned, Broke, Continued): pass
            return self.actions
        finally:
            self.actions = prior
            self.statics, self.position, self.pending_async = statics, position, pending

    def statement(self, node, frame):
        self.tick()
        if node is None: return
        k = node["kind"]
        if k == "BLOCK":
            for item in node["statements"]: self.statement(item, frame)
        elif k == "VARIABLE": frame.locals[node["name"]] = self.coerce(self.expr(node.get("value"), frame), node.get("type", ""))
        elif k == "EXPRESSION_STATEMENT":
            value = self.expr(node["expression"], frame)
            if isinstance(value, Unknown):
                self.problem("error", "UNSUPPORTED_CALL", value.detail, node, frame.cls)
                self.actions.append(_unknown_action(value.detail, self.source(node, frame.cls)))
        elif k == "RETURN": raise Returned(self.expr(node.get("expression"), frame))
        elif k == "THROW": raise Returned(Unknown("Source throws an exception"))
        elif k == "IF":
            condition = self.expr(node["condition"], frame)
            if isinstance(condition, bool): self.statement(node["yes"] if condition else node.get("no"), frame)
            else:
                self.problem("warning", "RUNTIME_BRANCH", "Runtime-dependent branch retained as alternatives", node, frame.cls)
                branches = [{"label": "condition true", "actions": self.capture(node["yes"], frame)},
                            {"label": "condition false", "actions": self.capture(node.get("no"), frame)}]
                self.actions.append({"kind": "branch", "branches": branches, "source": self.source(node, frame.cls)})
                # Later values assigned by either branch are not known; retain a barrier.
                self.actions.append(_unknown_action("Runtime branch may change subsequent state", self.source(node, frame.cls)))
        elif k == "FOR_LOOP":
            for item in node["init"]: self.statement(item, frame)
            for _ in range(MAX_LOOP):
                condition = self.expr(node["condition"], frame) if node.get("condition") else True
                if condition is False: break
                if condition is not True:
                    self.actions.append(_unknown_action("Runtime-dependent for loop", self.source(node, frame.cls)))
                    self.problem("error", "RUNTIME_LOOP", "Cannot bound runtime loop", node, frame.cls); break
                try: self.statement(node["body"], frame)
                except Broke: break
                except Continued: pass
                for item in node["update"]: self.statement(item, frame)
            else:
                self.problem("error", "LOOP_LIMIT", "Loop exceeds extraction bound", node, frame.cls)
                self.actions.append(_unknown_action("Loop bound exceeded", self.source(node, frame.cls)))
        elif k == "ENHANCED_FOR_LOOP":
            values = self.expr(node["expression"], frame)
            if isinstance(values, list) and len(values) <= MAX_LOOP:
                for value in values:
                    frame.locals[node["variable"]["name"]] = value
                    try: self.statement(node["body"], frame)
                    except Broke: break
                    except Continued: continue
            else:
                self.actions.append(_unknown_action("Unknown iterable", self.source(node, frame.cls)))
        elif k in ("WHILE_LOOP", "DO_WHILE_LOOP"):
            if k == "WHILE_LOOP" and self.recognized_busy_wait(node) and self.finish_async_wait():
                self.problem("warning","FOLLOWER_WAIT","Recognized follower busy wait; movement timing remains estimated",node,frame.cls)
                return
            condition = self.expr(node["condition"], frame)
            if condition is False and k == "WHILE_LOOP": return
            self.problem("error", "RUNTIME_LOOP", "Runtime loop is not converted into a guessed finite route", node, frame.cls)
            actions = self.capture(node["body"], frame)
            self.actions.append({"kind": "branch", "branches": [{"label": "loop body (repetition unknown)", "actions": actions}, {"label": "not entered", "actions": []}], "source": self.source(node, frame.cls)})
            self.actions.append(_unknown_action("Runtime loop duration/repetition unknown", self.source(node, frame.cls)))
        elif k == "SWITCH":
            self.problem("warning", "RUNTIME_SWITCH", "Switch alternatives retained; fall-through/control state requires review", node, frame.cls)
            branches = []
            for case in node["cases"]:
                body = case.get("body") or {"kind": "BLOCK", "statements": case.get("statements") or [], "line": case["line"]}
                branches.append({"label": ", ".join(case.get("labels") or ["default"]), "actions": self.capture(body, frame)})
            self.actions.append({"kind": "branch", "branches": branches, "source": self.source(node, frame.cls)})
            self.actions.append(_unknown_action("Switch control flow requires review", self.source(node, frame.cls)))
        elif k == "TRY":
            self.statement(node["body"], frame)
            if node.get("catches"):
                self.problem("warning", "EXCEPTION_PATH", "Exception handlers are additional unresolved outcomes", node, frame.cls)
                self.actions.append(_unknown_action("Exception recovery path requires review", self.source(node, frame.cls)))
            self.statement(node.get("finally"), frame)
        elif k == "SYNCHRONIZED": self.statement(node["body"], frame)
        elif k == "BREAK": raise Broke()
        elif k == "CONTINUE": raise Continued()
        elif k in ("EMPTY_STATEMENT",): pass
        else:
            self.problem("error", "UNSUPPORTED_STATEMENT", "Unsupported Java statement " + k, node, frame.cls)
            self.actions.append(_unknown_action("Unsupported Java statement " + k, self.source(node, frame.cls)))

    def entry_list(self):
        entries = []
        for name, cls in self.classes.items():
            if cls["kind"] not in ("CLASS", "RECORD") or "abstract" in cls.get("modifiers", ""): continue
            annotations = cls.get("annotations", [])
            annotated = any(a.startswith("@Autonomous") or ".Autonomous(" in a for a in annotations)
            disabled = any(a.startswith("@Disabled") or a.endswith(".Disabled") for a in annotations)
            if disabled: continue
            if annotated or (not any("@TeleOp" in a for a in annotations) and self.method(name, "runOpMode") and cls["name"] not in ("General", "AbstractAuto")):
                entries.append({"name": cls["name"], "qualified_name": name})
        return sorted(entries, key=lambda x: x["qualified_name"])

    def run(self, entry):
        self.entry = entry
        receiver = Instance(entry)
        for owner in self.hierarchy(entry):
            base = self.classes[owner].get("extends")
            if base and not self.resolve(base, owner) and base.rsplit(".",1)[-1] not in ("LinearOpMode", "OpMode", "Object"):
                self.problem("error", "MISSING_SUPERCLASS", "Missing superclass source: " + base, self.classes[owner], owner)
        # Java field initializers and source constructors are interpreted in base-to-leaf
        # order. Their code is still restricted to the same static, whitelisted model.
        for owner in reversed(list(self.hierarchy(entry))):
            self.ensure_static(owner)
            for item in self.classes[owner]["members"]:
                if item and item["kind"] == "VARIABLE" and item.get("value"):
                    self.field(owner, item["name"], receiver)
                elif item and item["kind"] == "BLOCK" and not item.get("static"):
                    self.statement(item, Frame(owner, receiver))
            constructor = next((x for x in self.classes[owner]["members"] if x and x["kind"] == "METHOD"
                                and x["name"] == "<init>" and not x["parameters"]), None)
            if constructor: self.invoke(receiver, (owner,constructor), [], self.classes[owner])
        alliance = self.method(entry, "autonomous_alliance", 0)
        if alliance:
            value = self.invoke(receiver, alliance, [], self.classes[entry])
            for owner in self.hierarchy(entry):
                if any(x and x["kind"] == "VARIABLE" and x["name"] == "alliance" for x in self.classes[owner]["members"]):
                    receiver.values[(owner,"alliance")] = value
        if self.options.get("alliance"):
            for owner in self.hierarchy(entry): receiver.values[(owner,"alliance")] = str(self.options["alliance"]).upper()
        start_method = self.method(entry, "starting_pose", 0)
        if start_method:
            start = self.invoke(receiver, start_method, [], self.classes[entry])
            if isinstance(start, Pose): self.position = start
            else: self.problem("error", "UNKNOWN_START", "Starting pose could not be resolved", self.classes[entry], entry)
        initial = self.position
        selected = self.method(entry, "build_autonomous", 0) or self.method(entry, "runOpMode", 0)
        if selected:
            self.invoke(receiver, selected, [], self.classes[entry])
        else:
            for hook in ("init", "start", "loop"):
                method = self.method(entry, hook, 0)
                if method: self.invoke(receiver, method, [], self.classes[entry])
            self.problem("error", "ITERATIVE_OPMODE", "Iterative OpMode loop requires state-machine review", self.classes[entry], entry)
            self.actions.append(_unknown_action("Iterative loop repetition unknown"))
        return initial


def import_java(sources: dict[str, str], entry: str | None = None, options: dict | None = None) -> dict:
    """Extract supported autonomous behavior from supplied Java files.

    ``options['list_entries']`` performs parsing/discovery only. A selection is
    required when several concrete autonomous entries exist. Missing dependencies,
    runtime branches and unsupported actions remain visible in diagnostics/actions.
    """
    options = options or {}
    if not isinstance(sources, dict) or not sources or not all(isinstance(k,str) and isinstance(v,str) for k,v in sources.items()):
        raise ValueError("Java input must map source filenames to Java text")
    if len(sources) > 2000 or sum(len(v) for v in sources.values()) > 12_000_000:
        raise ValueError("Java input exceeds 2000 files or 12 MB")
    parsed = _parse_cached(tuple(sorted(sources.items())))
    interpreter = Interpreter(parsed, options)
    entries = interpreter.entry_list()
    result = {"format": "java", "entries": entries, "selected_entry": None, "start_pose": None,
              "actions": [], "diagnostics": interpreter.diagnostics}
    if options.get("list_entries"): return result
    selected = None
    if entry:
        matches = [e for e in entries if entry in (e["name"], e["qualified_name"])]
        if len(matches) == 1: selected = matches[0]["qualified_name"]
        else:
            interpreter.problem("error", "ENTRY_NOT_FOUND", "Autonomous entry not found or ambiguous: " + entry)
            return result
    elif len(entries) == 1: selected = entries[0]["qualified_name"]
    elif len(entries) > 1:
        interpreter.problem("info", "ENTRY_SELECTION_REQUIRED", "Select one of the discovered autonomous entries")
        return result
    else:
        interpreter.problem("error", "NO_AUTONOMOUS_ENTRY", "No concrete autonomous entry was found; include its class and inherited sources")
        return result
    result["selected_entry"] = selected
    if any(p["severity"] == "error" for p in parsed["diagnostics"]): return result
    try:
        start = interpreter.run(selected)
        if isinstance(start, Pose): result["start_pose"] = {"x": start.x, "y": start.y, "heading_rad": start.heading}
    except (ValueError, TypeError, ArithmeticError, RecursionError) as failure:
        interpreter.problem("error", "EXTRACTION_LIMIT", "Extraction stopped: " + str(failure))
        interpreter.actions.append(_unknown_action("Extraction stopped before the full routine was resolved"))
    result["actions"] = interpreter.actions
    return result
