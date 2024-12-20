from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.generics import DestroyAPIView
from rest_framework.permissions import IsAuthenticated

from lucive.push_notifications import send_push_notification
from .models import Rule, RuleModificationRequest
from rest_framework import status
from .serializers import RuleSerializer, CreateRuleModificationRequestSerializer, RuleModificationRequestSerializer, UpdateRuleSerializer, CreateRuleSerializer, DeleteRuleSerializer
from duos.models import Duo
from django.db.models import Q
class UserRulesView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        user = request.user
        confirmed_duo = Duo.objects.filter(
            (Q(user1=user) | Q(user2=user))
        ).first()
        if not confirmed_duo:
            return Response({'error': 'User is not part of a confirmed duo'}, status=status.HTTP_403_FORBIDDEN)
        
        duo_partner = confirmed_duo.user1 if confirmed_duo.user2 == user else confirmed_duo.user2
        rules = Rule.objects.filter(Q(user=user) | Q(user=duo_partner))
        modification_requests = RuleModificationRequest.objects.filter(Q(user=user) | Q(user=duo_partner))
        combined_list = []
        mod_data = {}

        for mod_request in modification_requests:
            serializer = RuleModificationRequestSerializer(mod_request, context={'request': request})
            data = serializer.data
            mod_data[(mod_request.app, mod_request.user)] = data

        for rule in rules:
            serializer = RuleSerializer(rule, context={'request': request})
            data = serializer.data
            data['modificationData'] = mod_data.get((rule.app, rule.user), None)
            combined_list.append(data)

        return Response(combined_list)
    

class CreateRuleView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = CreateRuleSerializer(data=request.data, context={'request': request})
        if serializer.is_valid():
            rule = serializer.save()
            return Response(RuleSerializer(rule, context={'request': request}).data, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
    
class DeleteRuleView(DestroyAPIView):
    serializer_class = DeleteRuleSerializer
    permission_classes = [IsAuthenticated]

    def delete(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)
        serializer.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
    
class UpdateRuleView(APIView):
    permission_classes = [IsAuthenticated]

    def put(self, request):
        user = request.user
        app = request.data.get('app')
        if not app:
            return Response({"error": "App is required."}, status=status.HTTP_400_BAD_REQUEST)
        try:
            rule = Rule.objects.get(app=app, user=user)
        except Rule.DoesNotExist:
            return Response({"error": "Rule not found or not owned by user."}, status=status.HTTP_404_NOT_FOUND)
        try:
            duo = Duo.objects.get(Q(user1=user) | Q(user2=user))
        except Duo.DoesNotExist:
            return Response({"error": "User is not part of a confirmed duo."}, status=status.HTTP_403_FORBIDDEN)
        update_serializer = UpdateRuleSerializer(instance=rule, data=request.data, context={'request': request})
        if update_serializer.is_valid():
            rule = update_serializer.save()
            return Response(RuleSerializer(rule, context={'request': request}).data, status=status.HTTP_200_OK)
        create_request_serializer = CreateRuleModificationRequestSerializer(data=request.data, context={'request': request})
        if create_request_serializer.is_valid():
            mod_request = create_request_serializer.save()
            mod_data = RuleModificationRequestSerializer(mod_request, context={'request': request}).data
            resp = RuleSerializer(rule, context={'request': request}).data
            resp['modificationData'] = mod_data
            push_notification_data = {
                'title': 'Rule modification request',
                'body': f'{user.username} has requested a rule modification',
            }
            partner = duo.user1 if duo.user2 == user else duo.user2
            print(partner.fcm_token)
            send_push_notification(partner.fcm_token, **push_notification_data)
            return Response(resp, status=status.HTTP_201_CREATED)
        return Response(update_serializer.errors, status=status.HTTP_400_BAD_REQUEST)
    
class ApproveRuleModificationRequestView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        user = request.user
        app = request.data.get('app')
        try:
            duo = Duo.objects.get(Q(user1=user) | Q(user2=user))
            other_user = duo.user1 if duo.user2 == user else duo.user2
        except Duo.DoesNotExist:
            return Response({"error": "User is not part of a confirmed duo."}, status=status.HTTP_403_FORBIDDEN)
        if not app:
            return Response({"error": "App is required."}, status=status.HTTP_400_BAD_REQUEST)
        try:
            rule_mod_request = RuleModificationRequest.objects.get(app=app, user=other_user)
        except RuleModificationRequest.DoesNotExist:
            return Response({"error": "Rule modification request not found or not owned by user."}, status=status.HTTP_404_NOT_FOUND)
        try:
            rule = Rule.objects.get(app=app, user=other_user)
        except Rule.DoesNotExist:
            return Response({"error": "Rule not found."}, status=status.HTTP_404_NOT_FOUND)
        rule.daily_max_seconds = rule_mod_request.daily_max_seconds
        rule.hourly_max_seconds = rule_mod_request.hourly_max_seconds
        rule.session_max_seconds = rule_mod_request.session_max_seconds
        rule.is_daily_max_seconds_enforced = rule_mod_request.is_daily_max_seconds_enforced
        rule.is_hourly_max_seconds_enforced = rule_mod_request.is_hourly_max_seconds_enforced
        rule.is_session_max_seconds_enforced = rule_mod_request.is_session_max_seconds_enforced
        rule.is_active = rule_mod_request.is_active
        rule.daily_reset = rule_mod_request.daily_reset
        rule.intervention_type = rule_mod_request.intervention_type
        rule.is_startup_delay_enabled = rule_mod_request.is_startup_delay_enabled
        rule.save()
        rule_mod_request.delete()
        push_notification_data = {
            'title': 'Rule modification request approved',
            'body': f'{user.username} has approved your rule modification request',
        }
        send_push_notification(other_user.fcm_token, **push_notification_data)
        return Response(RuleSerializer(rule, context={'request': request}).data , status=status.HTTP_200_OK)
    
class DeleteRuleModificationRequestView(DestroyAPIView):
    permission_classes = [IsAuthenticated]

    def delete(self, request, *args, **kwargs):
        user = request.user
        app = request.data.get('app')
        if not app:
            return Response({"error": "App is required."}, status=status.HTTP_400_BAD_REQUEST)
        try:
            duo = Duo.objects.get(Q(user1=user) | Q(user2=user))
        except Duo.DoesNotExist:
            return Response({"error": "User is not part of a confirmed duo."}, status=status.HTTP_403_FORBIDDEN)
        try:
            rule_mod_request = RuleModificationRequest.objects.get(app=app, user=user)
        except RuleModificationRequest.DoesNotExist:
            return Response({"error": "Rule modification request not found or not owned by user."}, status=status.HTTP_404_NOT_FOUND)
        try:
            rule = Rule.objects.get(app=app, user=user)
        except Rule.DoesNotExist:
            return Response({"error": "Rule not found."}, status=status.HTTP_404_NOT_FOUND)
        rule_mod_request.delete()
        return Response(RuleSerializer(rule, context={'request': request}).data, status=status.HTTP_200_OK)
    